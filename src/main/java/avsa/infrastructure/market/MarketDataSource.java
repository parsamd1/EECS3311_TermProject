package avsa.infrastructure.market;

/** Somewhere listings can come from. Proxy pattern: the subject that real sources and the proxy share. */
public interface MarketDataSource {

    String name();

    boolean isAvailable();

    /** @throws MarketDataUnavailableException the source failed */
    MarketSearchResult search(MarketQuery query);
}
