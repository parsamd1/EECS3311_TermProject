package avsa.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.agent.MockLLMClient;
import avsa.domain.BodyStyle;
import avsa.domain.BuyerType;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.ExportedListing;
import avsa.domain.FuelType;
import avsa.domain.GeneratedListing;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.ListingEdit;
import avsa.domain.ListingOptions;
import avsa.domain.Location;
import avsa.domain.MarketSnapshot;
import avsa.domain.Money;
import avsa.domain.Offer;
import avsa.domain.OfferVerdict;
import avsa.domain.Platform;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.Session;
import avsa.domain.Severity;
import avsa.domain.StrategyType;
import avsa.domain.Transmission;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.AppConfig;
import avsa.infrastructure.JsonMapper;
import avsa.infrastructure.JsonSessionRepository;
import avsa.infrastructure.SchemaMigrator;
import avsa.infrastructure.market.MarketSourceType;
import avsa.services.SearchRequest;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private final MockLLMClient llm = new MockLLMClient();

    private AgentController controller() {
        return AvsaApplication.create(AppConfig.load(dir.resolve("none.env"), Map.of()), dir, CLOCK, llm);
    }

    private static VehicleProfile cx5() {
        return VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS").bodyStyle(BodyStyle.SUV)
                .odometerKm(49_000).transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE)
                .location(new Location("Toronto", "ON", 43.6532, -79.3832))
                .condition(new ConditionReport(null, List.of(new Defect(DefectType.SCRATCH, "rear door", Severity.MINOR,
                        "About 25 cm hairline", null))))
                .build();
    }

    @Test
    void theWholeSellingFlowRunsThroughTheFacadeAndIsAutoSaved() {
        AgentController c = controller();
        List<String> seen = new ArrayList<>();
        c.events().subscribe(AgentEvent.class, e -> seen.add(e.getClass().getSimpleName()));

        c.saveProfile(cx5());
        c.confirmHistory(new VehicleHistory(null, 2, 1, LienStatus.NONE, List.of("Ontario"), HistorySourceType.MANUAL,
                true, List.of(), List.of()));
        llm.thenRespond("{\"flags\": []}");
        MarketSnapshot snapshot = c.findComparables(SearchRequest.defaults(), MarketSourceType.SEEDED, null);
        assertEquals(8, snapshot.rankedListings().size());

        PriceRecommendation rec = c.recommendPrice(StrategyType.MAX_PROFIT, PricingParameters.defaults(), null);
        c.acceptPrice(rec);
        assertEquals(3, c.forecast(Money.cad(150)).points().size());
        GeneratedListing listing = c.generateListing(ListingOptions.defaults());
        assertTrue(listing.isExportable(), () -> listing.report().orElseThrow().violations().toString());
        ExportedListing exported = c.exportListing(Platform.KIJIJI, null);
        assertTrue(exported.text().contains("scratch"));
        assertEquals(OfferVerdict.LOW_BALL,
                c.evaluateOffer(new Offer(Money.cad(18_000), BuyerType.DEALER, "", CLOCK.instant())).verdict());

        assertEquals(List.of("ProfileUpdated", "HistoryUpdated", "MarketDataReady", "PriceRecommended",
                "SessionUpdated", "ListingGenerated", "SessionUpdated"), seen);

        Session saved = new JsonSessionRepository(dir.resolve("sessions"), new JsonMapper(), new SchemaMigrator())
                .load(c.sessions().current().id());
        assertEquals(1, saved.snapshots().size());
        assertEquals(rec.id(), saved.activePrice().orElseThrow().id());
        assertTrue(saved.forecast().isPresent());
        assertEquals(1, saved.listings().size());
        assertEquals(1, saved.offerLog().size());
    }

    @Test
    void theAiExplainsThePriceAndAnAcceptedProposalChangesItWithinTheCap() {
        AgentController c = controller();
        c.saveProfile(cx5());
        llm.thenRespond("{\"flags\": []}");
        c.findComparables(SearchRequest.defaults(), MarketSourceType.SEEDED, null);
        llm.thenRespond("{\"explanation\": \"Private buyers should pay within the retail range.\", \"adjustments\": "
                + "[{\"label\": \"Winter tires included\", \"percent\": 1.0, \"reason\": \"Extra set\"}]}");
        PriceRecommendation rec = c.recommendPrice(StrategyType.MAX_PROFIT, PricingParameters.defaults(), null);
        assertFalse(rec.templateExplanation());
        assertEquals(1, rec.proposals().size());

        PriceRecommendation active = c.acceptPrice(rec, List.of(rec.proposals().get(0).id()));
        assertTrue(active.askingPrice().compareTo(rec.askingPrice()) > 0, "winter tires raise the price");
        assertEquals("Private buyers should pay within the retail range.", active.explanation().orElseThrow());
        assertEquals(active.id(), c.sessions().current().activePrice().orElseThrow().id());
        assertTrue(active.adjustments().stream().anyMatch(a -> a.label().startsWith("Winter tires")));
    }

    @Test
    void anOversizedAiAdjustmentIsIgnoredByTheEngine() {
        AgentController c = controller();
        c.saveProfile(cx5());
        PriceRecommendation rec = c.recommendPrice(StrategyType.MAX_PROFIT, PricingParameters.defaults(), Money.cad(30_000));
        avsa.domain.PriceAdjustment huge = new avsa.domain.PriceAdjustment(java.util.UUID.randomUUID(), "Gold paint",
                Money.cad(5_000), avsa.domain.AdjustmentKind.AI_PROPOSED, false);
        rec.setProposals(List.of(huge));
        PriceRecommendation active = c.acceptPrice(rec, List.of(huge.id()));
        assertEquals(rec.askingPrice(), active.askingPrice());
        assertTrue(active.warnings().stream().anyMatch(w -> w.contains("exceeds the cap")));
    }

    @Test
    void editsAreValidatedAgainAndAFailingListingCanBeOverridden() {
        AgentController c = controller();
        c.saveProfile(cx5());
        llm.thenRespond("{\"flags\": []}");
        c.findComparables(SearchRequest.defaults(), MarketSourceType.SEEDED, null);
        c.acceptPrice(c.recommendPrice(StrategyType.QUICK_SALE, PricingParameters.defaults(), null));
        GeneratedListing listing = c.generateListing(ListingOptions.defaults());

        GeneratedListing hidden = c.editListing(new ListingEdit(listing.title(), listing.description(), List.of(),
                List.of(), "Flawless."));
        assertFalse(hidden.isExportable());
        assertThrows(IllegalStateException.class, () -> c.exportListing(Platform.KIJIJI, null));
        assertTrue(c.overrideListingValidation().isExportable());
        assertTrue(c.exportListing(Platform.KIJIJI, null).text().contains("Flawless."));
    }

    @Test
    void featuresNeedAProfileAndPricingNeedsComparablesOrABaseline() {
        AgentController c = controller();
        assertThrows(IllegalStateException.class,
                () -> c.findComparables(SearchRequest.defaults(), MarketSourceType.SEEDED, null));
        c.saveProfile(cx5());
        IllegalStateException noComps = assertThrows(IllegalStateException.class,
                () -> c.recommendPrice(StrategyType.MAX_PROFIT, PricingParameters.defaults(), null));
        assertTrue(noComps.getMessage().contains("manual baseline"));
        assertEquals(Money.cad(30_000),
                c.recommendPrice(StrategyType.MAX_PROFIT, PricingParameters.defaults(), Money.cad(30_000)).baseline());
        assertThrows(IllegalStateException.class, () -> c.generateListing(ListingOptions.defaults()));
    }

    @Test
    void invalidProfilesAreRejectedAndAChangedProfileMakesThePriceStale() {
        AgentController c = controller();
        assertThrows(IllegalArgumentException.class,
                () -> c.saveProfile(VehicleProfile.builder().make("Mazda").build()));
        c.saveProfile(cx5());
        c.acceptPrice(c.recommendPrice(StrategyType.MAX_PROFIT, PricingParameters.defaults(), Money.cad(30_000)));
        c.saveProfile(VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS").bodyStyle(BodyStyle.SUV)
                .odometerKm(52_000).transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE)
                .location(new Location("Toronto", "ON", 43.6532, -79.3832)).build());
        assertEquals(52_000, c.sessions().current().activeProfile().odometerKm());
        assertTrue(c.sessions().current().activePrice().orElseThrow().stale());
        assertEquals(1, c.sessions().list().size(), "the same vehicle keeps its session");
    }

    @Test
    void withoutAnApiKeyAiStepsFallBackAndSayWhy() {
        AgentController c = AvsaApplication.create(AppConfig.load(dir.resolve("none.env"), Map.of()), dir, CLOCK, null);
        c.saveProfile(cx5());
        MarketSnapshot s = c.findComparables(SearchRequest.defaults(), MarketSourceType.LIVE, null);
        assertEquals("Seeded dataset", s.sourceName());
        assertTrue(c.lastNotes().get(0).contains("unreviewed"));
    }

    @Test
    void eventBusDeliversBySubtypeAndUnsubscribes() {
        EventBus bus = new EventBus();
        List<String> got = new ArrayList<>();
        EventListener onlyPrices = e -> got.add("price");
        bus.subscribe(PriceRecommended.class, onlyPrices);
        bus.subscribe(AgentEvent.class, e -> got.add("any"));
        bus.publish(new PriceRecommended(CLOCK.instant(), java.util.UUID.randomUUID()));
        bus.publish(new ProfileUpdated(CLOCK.instant(), java.util.UUID.randomUUID()));
        bus.unsubscribe(PriceRecommended.class, onlyPrices);
        bus.publish(new PriceRecommended(CLOCK.instant(), java.util.UUID.randomUUID()));
        assertEquals(List.of("price", "any", "any", "any"), got);
    }
}
