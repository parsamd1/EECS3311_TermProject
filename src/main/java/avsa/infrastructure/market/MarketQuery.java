package avsa.infrastructure.market;

/** What to ask a data source for: one make and model within a range of model years. */
public record MarketQuery(String make, String model, int minYear, int maxYear) {

    boolean matches(RawListing raw) {
        if (!make.equalsIgnoreCase(strip(raw.make())) || !model.equalsIgnoreCase(strip(raw.model()))) {
            return false;
        }
        try {
            int year = Integer.parseInt(strip(raw.year()));
            return year >= minYear && year <= maxYear;
        } catch (NumberFormatException e) {
            return true; // Kept so that the normalizer rejects it and it is counted as skipped.
        }
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }
}
