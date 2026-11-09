package avsa.infrastructure.market;

/**
 * Proxy for the selected data source (F02). Asks the real source and caches what it returns;
 * if the source is unavailable or fails, returns the latest cached result, marked as such,
 * or else the fallback dataset. Callers use it like any other source.
 */
public final class CachingMarketDataSource implements MarketDataSource {

    private final MarketDataSource real;
    private final MarketDataSource fallback;
    private final ListingCache cache;

    public CachingMarketDataSource(MarketDataSource real, MarketDataSource fallback, ListingCache cache) {
        this.real = real;
        this.fallback = fallback;
        this.cache = cache;
    }

    @Override
    public String name() {
        return real.name();
    }

    @Override
    public boolean isAvailable() {
        return real.isAvailable() || fallback.isAvailable();
    }

    @Override
    public MarketSearchResult search(MarketQuery query) {
        if (real.isAvailable()) {
            try {
                MarketSearchResult result = real.search(query);
                cache.put(query, result);
                return result;
            } catch (MarketDataUnavailableException e) {
                // Handled below in the same way as a source that reports itself unavailable.
            }
        }
        return cache.latest(real.name(), query).map(MarketSearchResult::asCached).orElseGet(() -> fallback.search(query));
    }
}
