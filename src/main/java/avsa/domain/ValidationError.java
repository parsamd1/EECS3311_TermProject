package avsa.domain;

/**
 * A problem found while validating user input. Blocking errors stop the input
 * from being saved; non-blocking ones are warnings the user may accept.
 */
public record ValidationError(String field, String message, boolean blocking) {

    public static ValidationError error(String field, String message) {
        return new ValidationError(field, message, true);
    }

    public static ValidationError warning(String field, String message) {
        return new ValidationError(field, message, false);
    }
}
