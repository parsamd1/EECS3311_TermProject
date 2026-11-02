package avsa.agent;

import java.util.Optional;

/** The parsed value, or why the response could not be parsed. */
public record ParseResult<T>(T value, String error) {

    public static <T> ParseResult<T> ok(T value) {
        return new ParseResult<>(value, null);
    }

    public static <T> ParseResult<T> failed(String error) {
        return new ParseResult<>(null, error);
    }

    public boolean parsed() {
        return error == null;
    }

    public Optional<T> valueIfParsed() {
        return Optional.ofNullable(value);
    }
}
