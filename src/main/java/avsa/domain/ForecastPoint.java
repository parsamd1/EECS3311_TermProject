package avsa.domain;

/**
 * Projected value after {@code months}. Loss is measured at the middle of the
 * low-high band, relative to the reference price.
 */
public record ForecastPoint(int months, Money lowValue, Money highValue, Money lossDollars, double lossPercent,
                            Money cumulativeHoldingCost) {

    public Money midValue() {
        return lowValue.plus(highValue).times(0.5);
    }

    /** What waiting this long is expected to net the seller after holding costs. */
    public Money netIfSoldThen() {
        return midValue().minus(cumulativeHoldingCost);
    }
}
