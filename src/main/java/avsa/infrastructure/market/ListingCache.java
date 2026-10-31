package avsa.infrastructure.market;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The latest successful result per query, for the current run of the application. */
public final class ListingCache {

    private final Map<MarketQuery, MarketSearchResult> latest = new HashMap<>();

    public void put(MarketQuery query, MarketSearchResult result) {
        latest.put(query, result);
    }

    public Optional<MarketSearchResult> latest(MarketQuery query) {
        return Optional.ofNullable(latest.get(query));
    }
}
