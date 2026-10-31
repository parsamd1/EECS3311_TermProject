package avsa.infrastructure.market;

/** A market data source could not provide listings. */
public class MarketDataUnavailableException extends RuntimeException {
    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
