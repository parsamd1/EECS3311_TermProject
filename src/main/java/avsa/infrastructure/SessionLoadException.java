package avsa.infrastructure;

/** A saved session could not be loaded. */
public class SessionLoadException extends RuntimeException {
    public SessionLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
