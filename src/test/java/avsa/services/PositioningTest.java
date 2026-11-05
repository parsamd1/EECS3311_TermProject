package avsa.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.agent.AgentTrace;
import avsa.agent.AiTaskRunner;
import avsa.agent.MockLLMClient;
import avsa.agent.PromptComposer;
import avsa.agent.ResponseParser;
import avsa.agent.ToolArgs;
import avsa.agent.ToolResult;
import avsa.domain.BodyStyle;
import avsa.domain.Claim;
import avsa.domain.ClaimKind;
import avsa.domain.ComparableListing;
import avsa.domain.Confidence;
import avsa.domain.FuelType;
import avsa.domain.Location;
import avsa.domain.Money;
import avsa.domain.PercentileMetric;
import avsa.domain.PositioningReport;
import avsa.domain.PriceRange;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.StrategyType;
import avsa.domain.Transmission;
import avsa.domain.VehicleFacts;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.JsonMapper;
import avsa.tools.PositioningTool;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositioningTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private static final VehicleProfile CX5 = VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS")
            .bodyStyle(BodyStyle.SUV).odometerKm(49_000).transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE)
            .location(new Location("Toronto", "ON", 43.65, -79.38)).build();
    private static final PriceRecommendation PRICE = new PriceRecommendation(StrategyType.MAX_PROFIT, Money.cad(29_000),
            new PriceRange(Money.cad(26_600), Money.cad(28_000)), new PriceRange(Money.cad(22_900), Money.cad(24_100)),
            Money.cad(29_500), Money.cad(24_100), Confidence.HIGH, List.of(), List.of(), List.of(),
            PricingParameters.defaults(), CLOCK.instant());
    private static final List<ComparableListing> COMPS = List.of(
            comp("a", 2023, 51_000, 28_900), comp("b", 2022, 86_000, 24_800), comp("c", 2024, 45_000, 29_000),
            comp("d", 2022, 151_000, 19_770));

    private static ComparableListing comp(String id, int year, int km, long price) {
        return new ComparableListing(id, "Kijiji", year, "GS", km, Money.cad(price), 10, "", "");
    }

    private final MockLLMClient llm = new MockLLMClient();
    private final PositioningTool tool = new PositioningTool(new AiTaskRunner(new PromptComposer(), llm,
            new ResponseParser(new JsonMapper()), new AgentTrace(CLOCK)), new PositioningAnalyzer(), new ClaimValidator());

    private PositioningReport run() {
        ToolResult r = tool.execute(ToolArgs.of("profile", CX5, "facts", VehicleFacts.from(CX5, null, PRICE.askingPrice()),
                "price", PRICE, "comparables", COMPS));
        return r.valueAs(PositioningReport.class);
    }

    @Test
    void percentilesPlaceTheVehicleAmongItsComparables() {
        List<PercentileMetric> m = new PositioningAnalyzer().metrics(CX5, PRICE.askingPrice(), COMPS);
        assertEquals(100.0, m.get(0).percentile(), "asking is above every comparable");
        assertEquals(25.0, m.get(1).percentile(), "only one comparable has fewer km");
        assertEquals(0.0, m.get(2).percentile(), "no comparable is older");
    }

    @Test
    void supportedClaimsAreKeptAndUnsupportedOnesRemoved() {
        llm.thenRespond("{\"claims\": ["
                + "{\"kind\": \"ADVANTAGE\", \"text\": \"At 49,000 km it has lower mileage than the 86,000 km C2.\", "
                + "\"evidence\": [\"C2\", \"metric:odometer\"]},"
                + "{\"kind\": \"ADVANTAGE\", \"text\": \"Lower mileage than C3.\", \"evidence\": [\"C3\"]},"
                + "{\"kind\": \"ADVANTAGE\", \"text\": \"Cheaper than C2.\", \"evidence\": [\"C2\"]},"
                + "{\"kind\": \"WEAKNESS\", \"text\": \"Priced above C1 at $28,900.\", \"evidence\": [\"C1\"]},"
                + "{\"kind\": \"TALKING_POINT\", \"text\": \"It only has 30,000 km.\", \"evidence\": [\"metric:odometer\"]},"
                + "{\"kind\": \"REMEDY\", \"text\": \"Mention the service records.\", \"evidence\": [\"C9\"]},"
                + "{\"kind\": \"TALKING_POINT\", \"text\": \"Clean and ready.\", \"evidence\": []}"
                + "], \"priceJustification\": \"Similar cars list at $28,900 to $29,000.\"}");
        PositioningReport r = run();
        assertEquals(List.of("At 49,000 km it has lower mileage than the 86,000 km C2.", "Priced above C1 at $28,900."),
                r.claims().stream().map(Claim::text).toList());
        assertEquals(5, r.removedClaimCount());
        assertEquals("Similar cars list at $28,900 to $29,000.", r.priceJustification());
        assertTrue(r.notices().get(0).contains("5 AI claim(s) were removed"));
        assertEquals(List.of("b", "metric:odometer"), r.claims(ClaimKind.ADVANTAGE).get(0).evidenceRefs());
        assertFalse(r.metricsOnly());
    }

    @Test
    void aJustificationWithAnInventedPriceIsRemoved() {
        llm.thenRespond("{\"claims\": [{\"kind\": \"ADVANTAGE\", \"text\": \"Newer than C2.\", \"evidence\": [\"C2\"]}],"
                + " \"priceJustification\": \"Comparable cars sell for $35,000.\"}");
        PositioningReport r = run();
        assertEquals("", r.priceJustification());
        assertTrue(r.claims().isEmpty(), "C2 is the same year, so 'newer' is not true");
        assertEquals(2, r.removedClaimCount());
    }

    @Test
    void withoutTheAiOnlyTheMetricsAreShown() {
        llm.alwaysFail(MockLLMClient.FailureMode.UNAVAILABLE);
        PositioningReport r = run();
        assertTrue(r.metricsOnly());
        assertTrue(r.claims().isEmpty());
        assertEquals(3, r.metrics().size());
        assertTrue(r.notices().get(0).contains("only the metrics"));
    }
}
