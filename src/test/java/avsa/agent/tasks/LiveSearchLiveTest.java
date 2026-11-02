package avsa.agent.tasks;

import avsa.agent.AgentTrace;
import avsa.agent.AiTaskRunner;
import avsa.agent.ClaudeClient;
import avsa.agent.PromptComposer;
import avsa.agent.ResponseParser;
import avsa.domain.BodyStyle;
import avsa.domain.ComparableListing;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.FuelType;
import avsa.domain.Location;
import avsa.domain.MarketSnapshot;
import avsa.domain.PricingParameters;
import avsa.domain.PriceRecommendation;
import avsa.domain.Severity;
import avsa.domain.Transmission;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.AppConfig;
import avsa.infrastructure.JsonMapper;
import avsa.infrastructure.market.Gazetteer;
import avsa.infrastructure.market.ListingNormalizer;
import avsa.infrastructure.market.LiveSearchSource;
import avsa.services.ComparableFinder;
import avsa.services.MaxProfitStrategy;
import avsa.services.OutlierFilter;
import avsa.services.PricingEngine;
import avsa.services.PricingInput;
import avsa.services.SearchRequest;
import avsa.services.SimilarityScorer;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs a real live search for the author's car and prints what it found. It costs a few cents
 * and needs a key in .env. Runs only when AVSA_LIVE_TESTS=true.
 */
@EnabledIfEnvironmentVariable(named = "AVSA_LIVE_TESTS", matches = "true")
class LiveSearchLiveTest {

    @Test
    void searchesTheRealMarket() throws java.io.IOException {
        Clock clock = Clock.systemUTC();
        AgentTrace trace = new AgentTrace(clock);
        LiveSearchSource live = new LiveSearchSource(new AiTaskRunner(new PromptComposer(),
                new ClaudeClient(AppConfig.load()), new ResponseParser(new JsonMapper()), trace), clock);
        VehicleProfile cx5 = VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS")
                .bodyStyle(BodyStyle.SUV).odometerKm(49_000).transmission(Transmission.AUTOMATIC)
                .fuelType(FuelType.GASOLINE).location(new Location("Toronto", "ON", 43.6532, -79.3832))
                .condition(new ConditionReport(null, List.of(new Defect(DefectType.SCRATCH, "rear door", Severity.MINOR,
                        "", null)))).build();

        MarketSnapshot s;
        try {
            s = new ComparableFinder(live, new ListingNormalizer(new Gazetteer()), new Gazetteer(),
                    new OutlierFilter(), new SimilarityScorer(), clock).find(cx5, SearchRequest.defaults());
        } finally {
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                    "avsa-live-trace.json"), trace.toJson(new JsonMapper()));
            trace.entries().forEach(e -> System.out.println("LIVE trace " + e.kind() + " " + e.name() + " "
                    + (e.error() == null ? "" : e.error()) + " " + e.payload().replace('\n', ' ')
                    .substring(0, Math.min(700, e.payload().length()))));
        }
        for (ComparableListing l : s.rankedListings()) {
            System.out.printf("LIVE %.3f %d %s %d km $%.0f %.0f km %s %s%n", l.score(), l.year(), l.trim(), l.odometerKm(),
                    l.askingPrice().toDouble(), l.distanceKm(), l.source(), l.url());
        }
        System.out.println("LIVE source=" + s.sourceName() + " skipped=" + s.skippedRecords() + " radius="
                + s.radiusKm() + " warnings=" + s.warnings() + " stats=" + s.stats());
        if (s.rankedListings().size() >= 3) {
            PriceRecommendation r = new PricingEngine(clock).recommend(
                    new PricingInput(cx5, s.rankedListings(), PricingParameters.defaults()), new MaxProfitStrategy());
            System.out.println("LIVE retail=" + r.retail() + " wholesale=" + r.wholesale() + " asking=" + r.askingPrice()
                    + " confidence=" + r.confidence());
        }
    }
}
