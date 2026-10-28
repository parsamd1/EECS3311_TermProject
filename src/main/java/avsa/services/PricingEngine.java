package avsa.services;

import avsa.domain.AdjustmentKind;
import avsa.domain.ComparableListing;
import avsa.domain.Confidence;
import avsa.domain.Defect;
import avsa.domain.Money;
import avsa.domain.PriceAdjustment;
import avsa.domain.PriceRange;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.Severity;
import avsa.domain.ValidationError;
import avsa.domain.VehicleProfile;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic pricing (F03). Computes the baseline from the comparables,
 * applies condition deductions, derives the retail and wholesale ranges, and
 * asks the selected {@link PricingStrategy} for the asking price and floor.
 * The LLM never changes these numbers; it only explains them.
 */
public final class PricingEngine {

    static final int MIN_COMPARABLES = 3;
    static final int HIGH_CONFIDENCE_COMPARABLES = 8;
    /** Half-width of the retail range, as a fraction of its midpoint, is clamped to this band. */
    static final double MIN_RANGE_HALF_WIDTH = 0.02;
    static final double MAX_RANGE_HALF_WIDTH = 0.10;
    static final double FEW_COMPARABLES_HALF_WIDTH = 0.03;
    /** Above this relative interquartile spread the market probably mixes segments (e.g. trims). */
    static final double DISPERSION_WARNING = 0.30;

    private final Clock clock;

    public PricingEngine() {
        this(Clock.systemDefaultZone());
    }

    public PricingEngine(Clock clock) {
        this.clock = clock;
    }

    public PriceRecommendation recommend(PricingInput input, PricingStrategy strategy) {
        PricingParameters params = input.parameters();
        List<ValidationError> paramErrors = params.validate();
        if (!paramErrors.isEmpty()) {
            throw new IllegalArgumentException(paramErrors.get(0).message());
        }

        VehicleProfile profile = input.profile();
        List<ComparableListing> comps = input.comparables().stream().filter(c -> !c.excluded()).toList();
        List<String> warnings = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();
        List<PriceAdjustment> adjustments = new ArrayList<>();

        Money baseline;
        double halfWidth;
        if (comps.size() >= MIN_COMPARABLES || (!comps.isEmpty() && input.manualBaselineValue().isEmpty())) {
            Money raw = weightedMedian(comps.stream().map(ComparableListing::askingPrice).toList(), weights(comps));
            List<Money> adjusted = comps.stream()
                    .map(c -> odometerAdjusted(c, profile, params.odometerAdjustmentPerKm())).toList();
            baseline = weightedMedian(adjusted, weights(comps));
            Money odometerEffect = baseline.minus(raw);
            if (odometerEffect.compareTo(Money.ZERO) != 0) {
                adjustments.add(PriceAdjustment.computed(
                        "Odometer difference vs. comparables ($" + params.odometerAdjustmentPerKm() + "/km)",
                        odometerEffect, AdjustmentKind.ODOMETER));
            }
            halfWidth = comps.size() >= MIN_COMPARABLES ? rangeHalfWidth(adjusted, warnings) : FEW_COMPARABLES_HALF_WIDTH;
            assumptions.add("Baseline is the similarity-weighted median of " + comps.size()
                    + " comparable asking prices, adjusted for odometer.");
        } else if (input.manualBaselineValue().isPresent()) {
            baseline = input.manualBaselineValue().get();
            halfWidth = FEW_COMPARABLES_HALF_WIDTH;
            assumptions.add("Baseline was entered manually by the seller.");
        } else {
            throw new IllegalArgumentException("No comparable listings and no manual baseline: cannot price the vehicle.");
        }
        if (comps.size() < MIN_COMPARABLES) {
            warnings.add("Fewer than " + MIN_COMPARABLES + " comparable listings; the price is less reliable.");
        }

        Money value = baseline;
        for (PriceAdjustment a : conditionAdjustments(profile, baseline)) {
            adjustments.add(a);
            value = value.plus(a.amount());
        }

        double margin = params.negotiationMargin();
        Money expected = value.times(1 - margin);
        PriceRange retail = new PriceRange(expected.times(1 - halfWidth), expected.times(1 + halfWidth)).roundTo(100);
        PriceRange wholesale = retail.times(1 - params.dealerSpread()).roundTo(100);
        assumptions.add("Expected selling price is " + percent(margin) + " below asking (negotiation margin).");
        assumptions.add("Wholesale is " + percent(params.dealerSpread()) + " below retail (dealer spread).");

        Money asking = strategy.askingPrice(wholesale, retail, margin);
        Money floor = strategy.walkAwayFloor(wholesale, retail);
        Confidence confidence = confidence(comps.size(), input.manualBaselineValue().isPresent() && comps.size() < MIN_COMPARABLES);

        PriceRecommendation rec = new PriceRecommendation(strategy.type(), baseline, retail, wholesale, asking, floor,
                confidence, adjustments, assumptions, warnings, params, Instant.now(clock));
        checkSanity(rec);
        return rec;
    }

    private static List<Double> weights(List<ComparableListing> comps) {
        return comps.stream().map(c -> Math.max(c.score(), 0.01)).toList();
    }

    /** A comparable with more kilometres than the seller's car is worth less, so its price is adjusted up. */
    static Money odometerAdjusted(ComparableListing c, VehicleProfile profile, double perKm) {
        return c.askingPrice().plus(Money.cad((c.odometerKm() - profile.odometerKm()) * perKm));
    }

    /** The smallest value whose cumulative weight reaches half the total weight. */
    static Money weightedMedian(List<Money> values, List<Double> weights) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing(values::get));
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        double running = 0;
        for (int i : order) {
            running += weights.get(i);
            if (running >= total / 2) {
                return values.get(i);
            }
        }
        return values.get(order.get(order.size() - 1));
    }

    /**
     * Condition deductions (F03 step 2): a defect's repair estimate when the seller
     * gave one, otherwise a share of the baseline by severity.
     */
    static List<PriceAdjustment> conditionAdjustments(VehicleProfile profile, Money baseline) {
        List<PriceAdjustment> result = new ArrayList<>();
        for (Defect d : profile.condition().defects()) {
            Money deduction = d.estimate().orElseGet(() -> baseline.times(severityRate(d.severity())));
            String where = d.bodyLocation().isEmpty() ? "" : " (" + d.bodyLocation() + ")";
            result.add(PriceAdjustment.computed(
                    d.severity().name().toLowerCase() + " " + d.type().name().toLowerCase() + where,
                    deduction.times(-1), AdjustmentKind.CONDITION));
        }
        return result;
    }

    static double severityRate(Severity severity) {
        return switch (severity) {
            case MINOR -> 0.005;
            case MODERATE -> 0.015;
            case SEVERE -> 0.04;
        };
    }

    /** Half the relative interquartile range of the adjusted prices, clamped; warns on a very spread market. */
    private static double rangeHalfWidth(List<Money> adjusted, List<String> warnings) {
        List<Double> sorted = adjusted.stream().map(Money::toDouble).sorted().toList();
        double q1 = percentile(sorted, 0.25);
        double q3 = percentile(sorted, 0.75);
        double median = percentile(sorted, 0.5);
        double relativeIqr = median > 0 ? (q3 - q1) / median : 0;
        if (relativeIqr > DISPERSION_WARNING) {
            warnings.add("Comparable prices vary widely; the market may contain separate segments such as trims.");
        }
        return Math.min(MAX_RANGE_HALF_WIDTH, Math.max(MIN_RANGE_HALF_WIDTH, relativeIqr / 2));
    }

    static double percentile(List<Double> sorted, double p) {
        double pos = p * (sorted.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        return sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (pos - lo);
    }

    static Confidence confidence(int comparables, boolean manualBaseline) {
        if (manualBaseline || comparables < MIN_COMPARABLES) {
            return Confidence.LOW;
        }
        return comparables >= HIGH_CONFIDENCE_COMPARABLES ? Confidence.HIGH : Confidence.MEDIUM;
    }

    /** F03 step 7: every value positive, ranges ordered, nothing implausibly far from the baseline. */
    static void checkSanity(PriceRecommendation rec) {
        List<Money> all = List.of(rec.retail().low(), rec.wholesale().low(), rec.askingPrice(), rec.walkAwayFloor());
        if (all.stream().anyMatch(m -> !m.isPositive())) {
            throw new IllegalStateException("Pricing produced a non-positive value.");
        }
        if (rec.walkAwayFloor().compareTo(rec.askingPrice()) > 0) {
            throw new IllegalStateException("Walk-away floor is above the asking price.");
        }
        double ratio = rec.askingPrice().toDouble() / rec.baseline().toDouble();
        if (ratio < 0.5 || ratio > 1.5) {
            throw new IllegalStateException("Asking price is implausibly far from the baseline.");
        }
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 1000) / 10.0 + "%";
    }
}
