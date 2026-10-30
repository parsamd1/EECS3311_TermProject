package avsa.infrastructure.history;

/** The file is corrupt or unreadable; nothing is stored. */
public class HistoryFileException extends RuntimeException {
    public HistoryFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
