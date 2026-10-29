package avsa.services;

/** Annual depreciation rates; {@code low <= high}. */
public record RateBand(double low, double high) {

    public RateBand {
        if (low < 0 || high >= 1 || low > high) {
            throw new IllegalArgumentException("Invalid rate band: " + low + " - " + high);
        }
    }
}
