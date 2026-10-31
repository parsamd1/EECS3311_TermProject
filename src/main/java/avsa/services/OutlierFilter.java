package avsa.services;

import avsa.domain.ComparableListing;
import java.util.List;

/** Removes listings priced outside 1.5 times the interquartile range (F02). */
public final class OutlierFilter {

    /** Quartiles mean little with fewer listings than this. */
    static final int MIN_LISTINGS = 4;
    private static final double IQR_FACTOR = 1.5;

    public List<ComparableListing> removeOutliers(List<ComparableListing> listings) {
        if (listings.size() < MIN_LISTINGS) {
            return listings;
        }
        List<Double> sorted = listings.stream().map(l -> l.askingPrice().toDouble()).sorted().toList();
        double q1 = PricingEngine.percentile(sorted, 0.25);
        double q3 = PricingEngine.percentile(sorted, 0.75);
        double margin = IQR_FACTOR * (q3 - q1);
        return listings.stream().filter(l -> {
            double price = l.askingPrice().toDouble();
            return price >= q1 - margin && price <= q3 + margin;
        }).toList();
    }
}
