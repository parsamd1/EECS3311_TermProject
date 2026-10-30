package avsa.infrastructure.history;

/** The file was readable but is not a format we can parse; the seller should use manual entry. */
public class UnsupportedHistoryFormatException extends RuntimeException {
    public UnsupportedHistoryFormatException(String message) {
        super(message);
    }
}
