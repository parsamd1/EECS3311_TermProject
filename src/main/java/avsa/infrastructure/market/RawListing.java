package avsa.infrastructure.market;

/**
 * One record exactly as a data source provided it; {@link ListingNormalizer} checks and converts it.
 *
 * @param locationAssumed true when the source did not give the city and the searched city was used instead
 */
public record RawListing(String source, String url, String listingDate, String make, String model, String year,
                         String trim, String odometerKm, String askingPrice, String city, String province,
                         String titleStatus, String accidents, String description, boolean locationAssumed) {

    public RawListing(String source, String url, String listingDate, String make, String model, String year,
                      String trim, String odometerKm, String askingPrice, String city, String province,
                      String titleStatus, String accidents, String description) {
        this(source, url, listingDate, make, model, year, trim, odometerKm, askingPrice, city, province, titleStatus,
                accidents, description, false);
    }
}
