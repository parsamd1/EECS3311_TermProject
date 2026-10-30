package avsa.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.AccidentRecord;
import avsa.domain.AdjustmentKind;
import avsa.domain.BodyStyle;
import avsa.domain.ComparableListing;
import avsa.domain.ConditionReport;
import avsa.domain.Confidence;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.FuelType;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.Location;
import avsa.domain.Money;
import avsa.domain.PriceAdjustment;
import avsa.domain.PriceRange;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.Severity;
import avsa.domain.StrategyType;
import avsa.domain.Transmission;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingEngineTest {

    private final PricingEngine engine =
            new PricingEngine(Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC));
    private final PricingParameters params = PricingParameters.defaults();

    private static VehicleProfile.Builder car() {
        return VehicleProfile.builder().make("Honda").model("Civic").year(2020).trim("EX")
                .bodyStyle(BodyStyle.SEDAN).odometerKm(80_000).transmission(Transmission.AUTOMATIC)
                .fuelType(FuelType.GASOLINE).location(new Location("Toronto", "ON", 43.65, -79.38))
                .condition(ConditionReport.noDefects(null));
    }

    private static ComparableListing comp(String id, long price, int odometerKm) {
        return new ComparableListing(id, "Kijiji", 2020, "EX", odometerKm, Money.cad(price), 10, "", "");
    }

    private static List<ComparableListing> sameComps(int n, long price) {
        List<ComparableListing> comps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            comps.add(comp("c" + i, price, 80_000));
        }
        return comps;
    }

    private PriceRecommendation price(VehicleProfile profile, List<ComparableListing> comps, StrategyType type) {
        return engine.recommend(new PricingInput(profile, comps, params).withHistory(cleanHistory()),
                PricingStrategy.of(type));
    }

    private static VehicleHistory history(int owners, boolean accidentsKnown, List<AccidentRecord> accidents) {
        return new VehicleHistory(null, owners, 1, LienStatus.NONE, List.of("Ontario"), HistorySourceType.MANUAL,
                accidentsKnown, accidents, List.of());
    }

    private static VehicleHistory cleanHistory() {
        return history(2, true, List.of());
    }

    @Test
    void identicalComparablesGiveExpectedLadder() {
        PriceRecommendation max = price(car().build(), sameComps(5, 25_000), StrategyType.MAX_PROFIT);
        assertEquals(Money.cad(25_000), max.baseline());
        // expected selling price 25,000 x 0.95 = 23,750; minimum +/-2% range, rounded to $100
        assertEquals(new PriceRange(Money.cad(23_300), Money.cad(24_200)), max.retail());
        // dealer spread 14%: 23,300 x 0.86 = 20,038 and 24,200 x 0.86 = 20,812
        assertEquals(new PriceRange(Money.cad(20_000), Money.cad(20_800)), max.wholesale());
        assertEquals(Money.cad(25_500), max.askingPrice(), "24,200 / 0.95 rounded");
        assertEquals(Money.cad(20_800), max.walkAwayFloor(), "top of wholesale");
        assertEquals(Confidence.MEDIUM, max.confidence());
        assertTrue(max.adjustments().isEmpty());
        assertTrue(max.warnings().isEmpty());

        PriceRecommendation quick = price(car().build(), sameComps(5, 25_000), StrategyType.QUICK_SALE);
        assertEquals(Money.cad(20_800), quick.askingPrice(), "top of wholesale");
        assertEquals(Money.cad(20_000), quick.walkAwayFloor(), "bottom of wholesale");
    }

    @Test
    void comparablesAreAlignedToTheSellersOdometer() {
        ComparableListing higherMileage = comp("a", 20_000, 90_000);
        assertEquals(Money.cad(20_500), PricingEngine.odometerAdjusted(higherMileage, car().build(), 0.05));
        ComparableListing lowerMileage = comp("b", 20_000, 70_000);
        assertEquals(Money.cad(19_500), PricingEngine.odometerAdjusted(lowerMileage, car().build(), 0.05));
    }

    @Test
    void odometerEffectIsItemized() {
        List<ComparableListing> comps = List.of(comp("a", 25_000, 100_000), comp("b", 25_000, 100_000),
                comp("c", 25_000, 100_000));
        PriceRecommendation rec = price(car().build(), comps, StrategyType.MAX_PROFIT);
        assertEquals(Money.cad(26_000), rec.baseline());
        assertEquals(AdjustmentKind.ODOMETER, rec.adjustments().get(0).kind());
        assertEquals(Money.cad(1_000), rec.adjustments().get(0).amount());
    }

    @Test
    void weightedMedianFavoursMoreSimilarListings() {
        List<Money> values = List.of(Money.cad(10), Money.cad(20), Money.cad(30));
        assertEquals(Money.cad(20), PricingEngine.weightedMedian(values, List.of(1.0, 1.0, 1.0)));
        assertEquals(Money.cad(30), PricingEngine.weightedMedian(values, List.of(1.0, 1.0, 5.0)));
    }

    @Test
    void defectsAreDeductedByEstimateOrSeverity() {
        ConditionReport condition = new ConditionReport(null, List.of(
                new Defect(DefectType.DENT, "rear door", Severity.SEVERE, "", null),
                new Defect(DefectType.SCRATCH, "hood", Severity.MINOR, "", Money.cad(500))));
        PriceRecommendation rec = price(car().condition(condition).build(), sameComps(5, 25_000),
                StrategyType.MAX_PROFIT);
        List<Money> deductions = rec.adjustments().stream()
                .filter(a -> a.kind() == AdjustmentKind.CONDITION).map(PriceAdjustment::amount).toList();
        assertEquals(List.of(Money.cad(-1_000), Money.cad(-500)), deductions, "4% of 25,000; repair estimate");
        // value 23,500 x 0.95 = 22,325 +/- 2%
        assertEquals(new PriceRange(Money.cad(21_900), Money.cad(22_800)), rec.retail());
    }

    @Test
    void accidentsAndExtraOwnersAreDeducted() {
        VehicleHistory h = history(4, true, List.of(new AccidentRecord(null, Severity.SEVERE, "front"),
                new AccidentRecord(null, null, "unknown severity")));
        PriceRecommendation rec = engine.recommend(
                new PricingInput(car().build(), sameComps(5, 25_000), params).withHistory(h), new MaxProfitStrategy());
        List<Money> deductions = rec.adjustments().stream()
                .filter(a -> a.kind() == AdjustmentKind.HISTORY).map(PriceAdjustment::amount).toList();
        assertEquals(List.of(Money.cad(-2_000), Money.cad(-1_000), Money.cad(-500)), deductions,
                "8% severe; 4% when severity is not stated; 1% for each of 2 owners beyond the second");
    }

    @Test
    void cleanHistoryHasNoDeduction() {
        PriceRecommendation rec = price(car().build(), sameComps(5, 25_000), StrategyType.MAX_PROFIT);
        assertTrue(rec.adjustments().isEmpty());
    }

    @Test
    void missingHistoryIsNeutralButLowersConfidence() {
        PriceRecommendation none = engine.recommend(new PricingInput(car().build(), sameComps(5, 25_000), params),
                new MaxProfitStrategy());
        assertEquals(Confidence.LOW, none.confidence());
        assertEquals(Money.cad(25_000), none.baseline());
        assertTrue(none.adjustments().isEmpty());
        assertTrue(none.assumptions().stream().anyMatch(a -> a.startsWith("No vehicle history")));

        PriceRecommendation many = engine.recommend(new PricingInput(car().build(), sameComps(8, 25_000), params),
                new MaxProfitStrategy());
        assertEquals(Confidence.MEDIUM, many.confidence());
    }

    @Test
    void unknownAccidentHistoryLowersConfidence() {
        PriceRecommendation rec = engine.recommend(new PricingInput(car().build(), sameComps(8, 25_000), params)
                .withHistory(history(1, false, List.of())), new MaxProfitStrategy());
        assertEquals(Confidence.MEDIUM, rec.confidence());
        assertTrue(rec.assumptions().stream().anyMatch(a -> a.startsWith("Accident history is unknown")));
    }

    @Test
    void excludedComparablesAreIgnored() {
        List<ComparableListing> comps = sameComps(3, 25_000);
        ComparableListing outlier = comp("x", 60_000, 80_000);
        outlier.setExcluded(true);
        List<ComparableListing> all = new ArrayList<>(comps);
        all.add(outlier);
        assertEquals(Money.cad(25_000), price(car().build(), all, StrategyType.MAX_PROFIT).baseline());
    }

    @Test
    void fewComparablesLowerConfidenceAndWarn() {
        PriceRecommendation rec = price(car().build(), sameComps(2, 25_000), StrategyType.MAX_PROFIT);
        assertEquals(Confidence.LOW, rec.confidence());
        assertTrue(rec.warnings().get(0).startsWith("Fewer than 3"));
    }

    @Test
    void manyComparablesGiveHighConfidence() {
        assertEquals(Confidence.HIGH, price(car().build(), sameComps(8, 25_000), StrategyType.MAX_PROFIT).confidence());
    }

    @Test
    void manualBaselineIsUsedWhenComparablesAreScarce() {
        PricingInput input = new PricingInput(car().build(), List.of(), params, Money.cad(30_000));
        PriceRecommendation rec = engine.recommend(input, new MaxProfitStrategy());
        assertEquals(Money.cad(30_000), rec.baseline());
        assertEquals(Confidence.LOW, rec.confidence());
    }

    @Test
    void noComparablesAndNoManualBaselineIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.recommend(new PricingInput(car().build(), List.of(), params), new MaxProfitStrategy()));
    }

    @Test
    void invalidParametersAreRejected() {
        PricingInput input = new PricingInput(car().build(), sameComps(5, 25_000), params.withDealerSpread(1.0));
        assertThrows(IllegalArgumentException.class, () -> engine.recommend(input, new QuickSaleStrategy()));
        assertTrue(params.withNegotiationMargin(-0.1).validate().size() == 1);
    }

    @Test
    void widelyDispersedPricesWarnAndWidenTheRange() {
        List<ComparableListing> comps = List.of(comp("a", 15_000, 80_000), comp("b", 20_000, 80_000),
                comp("c", 25_000, 80_000), comp("d", 30_000, 80_000), comp("e", 35_000, 80_000));
        PriceRecommendation rec = price(car().build(), comps, StrategyType.MAX_PROFIT);
        assertTrue(rec.warnings().stream().anyMatch(w -> w.contains("separate segments")));
        // 25,000 x 0.95 = 23,750 with the maximum +/-10% half-width
        assertEquals(new PriceRange(Money.cad(21_400), Money.cad(26_100)), rec.retail());
    }

    @Test
    void floorNeverExceedsAskingForEitherStrategy() {
        for (StrategyType type : StrategyType.values()) {
            PriceRecommendation rec = price(car().build(), sameComps(5, 18_000), type);
            assertTrue(rec.walkAwayFloor().compareTo(rec.askingPrice()) <= 0, type.name());
        }
    }
}
