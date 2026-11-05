package avsa.domain;

/**
 * Where the seller's vehicle sits among the comparables on one measure (F05).
 *
 * @param percentile share of comparables with a lower value, from 0 to 100
 * @param betterWhenLower true for price and odometer, where a lower value is the advantage
 */
public record PercentileMetric(String name, double value, double percentile, boolean betterWhenLower) {
}
