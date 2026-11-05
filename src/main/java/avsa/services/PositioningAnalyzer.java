package avsa.services;

import avsa.domain.ComparableListing;
import avsa.domain.Money;
import avsa.domain.PercentileMetric;
import avsa.domain.VehicleProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Computes where the vehicle sits among its comparables (F05). Owner counts are not known for
 * comparables, so owners are not compared.
 */
public final class PositioningAnalyzer {

    public static final String PRICE = "price";
    public static final String ODOMETER = "odometer";
    public static final String YEAR = "year";

    public List<PercentileMetric> metrics(VehicleProfile profile, Money askingPrice, List<ComparableListing> comps) {
        List<ComparableListing> used = comps.stream().filter(c -> !c.excluded()).toList();
        List<PercentileMetric> metrics = new ArrayList<>();
        metrics.add(metric(PRICE, askingPrice.toDouble(), used, c -> c.askingPrice().toDouble(), true));
        metrics.add(metric(ODOMETER, profile.odometerKm(), used, ComparableListing::odometerKm, true));
        metrics.add(metric(YEAR, profile.year(), used, ComparableListing::year, false));
        return metrics;
    }

    private static PercentileMetric metric(String name, double value, List<ComparableListing> comps,
                                           ToDoubleFunction<ComparableListing> of, boolean betterWhenLower) {
        long below = comps.stream().filter(c -> of.applyAsDouble(c) < value).count();
        double percentile = comps.isEmpty() ? 50 : 100.0 * below / comps.size();
        return new PercentileMetric(name, value, Math.round(percentile * 10) / 10.0, betterWhenLower);
    }
}
