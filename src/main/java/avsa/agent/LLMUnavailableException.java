package avsa.agent;

/** The language model could not be reached, timed out, or rejected the request. */
public class LLMUnavailableException extends RuntimeException {
    public LLMUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
