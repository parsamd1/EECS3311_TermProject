package avsa.infrastructure.market;

import java.time.Instant;
import java.util.List;

/**
 * Listings from one data source.
 *
 * @param skippedRecords malformed records the source could not read
 * @param fromCache      true when these are earlier results, retrieved at {@code retrievedAt}
 */
public record MarketSearchResult(String sourceName, List<RawListing> listings, int skippedRecords, boolean fromCache,
                                 Instant retrievedAt) {

    public MarketSearchResult {
        listings = List.copyOf(listings);
    }

    MarketSearchResult asCached() {
        return new MarketSearchResult(sourceName, listings, skippedRecords, true, retrievedAt);
    }
}
