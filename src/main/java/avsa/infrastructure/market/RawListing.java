package avsa.infrastructure.market;

/** One record exactly as a data source provided it; {@link ListingNormalizer} checks and converts it. */
public record RawListing(String source, String url, String listingDate, String make, String model, String year,
                         String trim, String odometerKm, String askingPrice, String city, String province,
                         String titleStatus, String accidents, String description) {
}
