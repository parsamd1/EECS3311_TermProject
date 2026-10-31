package avsa.infrastructure.market;

/**
 * Placeholder for a live listings service. No public listings API is configured, so this
 * source always reports itself unavailable and the proxy falls back to cached or seeded data.
 */
public final class LiveSearchSource implements MarketDataSource {

    @Override
    public String name() {
        return "Live search";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public MarketSearchResult search(MarketQuery query) {
        throw new MarketDataUnavailableException("Live search is not configured.", null);
    }
}
