package avsa.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.agent.AgentTrace;
import avsa.agent.AiTaskRunner;
import avsa.agent.MockLLMClient;
import avsa.agent.PromptComposer;
import avsa.agent.ResponseParser;
import avsa.agent.ToolArgs;
import avsa.agent.ToolManager;
import avsa.agent.ToolResult;
import avsa.agent.TraceKind;
import avsa.domain.BodyStyle;
import avsa.domain.ComparableListing;
import avsa.domain.ConditionReport;
import avsa.domain.ExportedListing;
import avsa.domain.FlagCode;
import avsa.domain.FuelType;
import avsa.domain.GeneratedListing;
import avsa.domain.ListingOptions;
import avsa.domain.Location;
import avsa.domain.MarketSnapshot;
import avsa.domain.Platform;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.StrategyType;
import avsa.domain.Transmission;
import avsa.domain.VehicleFacts;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.JsonMapper;
import avsa.infrastructure.PlatformProfileLoader;
import avsa.infrastructure.market.Gazetteer;
import avsa.infrastructure.market.ListingCache;
import avsa.infrastructure.market.LiveSearchSource;
import avsa.infrastructure.market.MarketSourceType;
import avsa.infrastructure.market.MarketSources;
import avsa.services.ExporterFactory;
import avsa.services.ListingValidator;
import avsa.services.PricingEngine;
import avsa.services.PricingInput;
import avsa.services.TemplateListingWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);

    private final MockLLMClient llm = new MockLLMClient();
    private final AgentTrace trace = new AgentTrace(CLOCK);
    private final AiTaskRunner ai = new AiTaskRunner(new PromptComposer(), llm, new ResponseParser(new JsonMapper()), trace);
    private final ToolManager tools = new ToolManager(trace);

    @TempDir
    Path dir;

    private static VehicleProfile cx5() {
        return VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS").bodyStyle(BodyStyle.SUV)
                .odometerKm(49_000).transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE)
                .location(new Location("Toronto", "ON", 43.6532, -79.3832)).condition(ConditionReport.noDefects(null))
                .build();
    }

    private MarketSearchTool marketTool() {
        MarketSources sources = new MarketSources(Path.of("data", "listings.csv"), new LiveSearchSource(),
                new ListingCache(), CLOCK);
        return new MarketSearchTool(ai, sources, new Gazetteer(), CLOCK);
    }

    @Test
    void theManagerRunsToolsByNameAndRecordsEachCall() {
        tools.register(marketTool());
        llm.thenRespond("{\"flags\": []}");
        ToolResult result = tools.execute(MarketSearchTool.NAME, ToolArgs.of("profile", cx5()));
        assertTrue(result.ok());
        assertEquals(8, result.valueAs(MarketSnapshot.class).rankedListings().size());
        assertEquals(TraceKind.TOOL_CALL, trace.entries().get(trace.entries().size() - 1).kind());
        assertFalse(trace.entries().get(trace.entries().size() - 1).payload().contains("Mazda"), "no values in the trace");
    }

    @Test
    void unknownToolsAndToolErrorsBecomeFailedResults() {
        assertFalse(tools.execute("nope", ToolArgs.of()).ok());
        tools.register(marketTool());
        ToolResult missing = tools.execute(MarketSearchTool.NAME, ToolArgs.of());
        assertFalse(missing.ok());
        assertEquals("Missing argument: profile", missing.error());
        assertEquals("Missing argument: profile", trace.entries().get(trace.entries().size() - 1).error());
    }

    @Test
    void theAiReviewFlagsListingsWithFixedPenalties() {
        MarketSearchTool tool = marketTool();
        llm.thenRespond("{\"flags\": []}");
        MarketSnapshot clean = tool.execute(ToolArgs.of("profile", cx5())).valueAs(MarketSnapshot.class);
        ComparableListing first = clean.rankedListings().get(0);
        assertTrue(clean.rankedListings().stream().allMatch(ComparableListing::reviewed));

        llm.thenRespond("{\"flags\": [{\"listingId\": \"" + first.listingId()
                + "\", \"code\": \"TRIM_MISMATCH\", \"reason\": \"Text says GT\"}]}");
        MarketSnapshot flagged = tool.execute(ToolArgs.of("profile", cx5())).valueAs(MarketSnapshot.class);
        ComparableListing same = flagged.rankedListings().stream()
                .filter(l -> l.listingId().equals(first.listingId())).findFirst().orElseThrow();
        assertEquals(FlagCode.TRIM_MISMATCH, same.flags().get(0).code());
        assertEquals(first.score() - 0.20, same.score(), 1e-9);
    }

    @Test
    void anUnknownFlagCodeIsRepairedAndAnUnavailableReviewLeavesListingsUnreviewed() {
        MarketSearchTool tool = marketTool();
        llm.thenRespond("{\"flags\": [{\"listingId\": \"x\", \"code\": \"LOOKS_DODGY\", \"reason\": \"\"}]}")
                .thenRespond("{\"flags\": []}");
        assertTrue(tool.execute(ToolArgs.of("profile", cx5())).ok());
        assertTrue(llm.received().get(1).user().contains("rejected"));

        llm.alwaysFail(MockLLMClient.FailureMode.UNAVAILABLE);
        ToolResult result = tool.execute(ToolArgs.of("profile", cx5()));
        assertTrue(result.notes().get(0).contains("unreviewed"));
        assertTrue(result.valueAs(MarketSnapshot.class).rankedListings().stream().noneMatch(ComparableListing::reviewed));
    }

    @Test
    void choosingLiveWithoutAKeyFallsBackToTheSeededDatasetAndSaysSo() {
        llm.thenRespond("{\"flags\": []}");
        MarketSnapshot s = marketTool().execute(ToolArgs.of("profile", cx5(), "source", MarketSourceType.LIVE))
                .valueAs(MarketSnapshot.class);
        assertEquals("Seeded dataset", s.sourceName());
        assertTrue(s.warnings().get(0).contains("Live search was unavailable"));
    }

    @Test
    void pricingListingAndExportToolsWorkTogether() throws IOException {
        llm.thenRespond("{\"flags\": []}");
        MarketSnapshot s = marketTool().execute(ToolArgs.of("profile", cx5())).valueAs(MarketSnapshot.class);
        PriceRecommendation rec = new PricingTool(ai, new PricingEngine(CLOCK)).execute(ToolArgs.of("input",
                new PricingInput(cx5(), s.rankedListings(), PricingParameters.defaults()), "strategy",
                StrategyType.MAX_PROFIT)).valueAs(PriceRecommendation.class);
        assertTrue(rec.templateExplanation());
        assertTrue(rec.explanation().orElseThrow().contains(rec.askingPrice().toString()));

        GeneratedListing listing = new ListingTool(ai, new TemplateListingWriter(), new ListingValidator(CLOCK))
                .execute(ToolArgs.of("facts", VehicleFacts.from(cx5(), null, rec.askingPrice()), "options",
                        ListingOptions.defaults())).valueAs(GeneratedListing.class);
        assertTrue(listing.isExportable(), () -> listing.report().orElseThrow().violations().toString());

        Path out = dir.resolve("kijiji.txt");
        ExportedListing exported = new ExportTool(new ExporterFactory(new PlatformProfileLoader(), CLOCK))
                .execute(ToolArgs.of("listing", listing, "platform", Platform.KIJIJI, "out", out))
                .valueAs(ExportedListing.class);
        assertEquals(out, exported.savedTo().orElseThrow());
        assertEquals(exported.text(), Files.readString(out));

        ToolResult unwritable = new ExportTool(new ExporterFactory(new PlatformProfileLoader(), CLOCK)).execute(
                ToolArgs.of("listing", listing, "platform", Platform.KIJIJI, "out", dir.resolve("missing/x.txt")));
        assertTrue(unwritable.ok());
        assertTrue(unwritable.notes().get(0).contains("Copy the text"));
    }
}
