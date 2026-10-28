package avsa.domain;

import java.util.Objects;

/** An inclusive price range with {@code low <= high}. */
public record PriceRange(Money low, Money high) {

    public PriceRange {
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(high, "high");
        if (low.compareTo(high) > 0) {
            throw new IllegalArgumentException("low " + low + " is above high " + high);
        }
    }

    public boolean contains(Money amount) {
        return amount.compareTo(low) >= 0 && amount.compareTo(high) <= 0;
    }

    public Money midpoint() {
        return low.plus(high).times(0.5);
    }

    /** 0 at {@code low}, 1 at {@code high}; below 0 or above 1 when outside the range. */
    public double positionOf(Money amount) {
        double width = high.toDouble() - low.toDouble();
        if (width == 0) {
            return amount.compareTo(low) < 0 ? -1 : amount.compareTo(high) > 0 ? 2 : 0.5;
        }
        return (amount.toDouble() - low.toDouble()) / width;
    }

    public PriceRange times(double factor) {
        return new PriceRange(low.times(factor), high.times(factor));
    }

    public PriceRange roundTo(long step) {
        return new PriceRange(low.roundTo(step), high.roundTo(step));
    }

    @Override
    public String toString() {
        return low + " - " + high;
    }
}
