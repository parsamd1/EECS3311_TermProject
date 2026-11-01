package avsa.services;

/** A listing cannot be exported; the message says what the seller must fix first. */
public class ExportBlockedException extends RuntimeException {
    public ExportBlockedException(String message) {
        super(message);
    }
}
