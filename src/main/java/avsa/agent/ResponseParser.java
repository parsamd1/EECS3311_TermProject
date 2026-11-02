package avsa.agent;

import avsa.infrastructure.JsonMapper;
import java.io.IOException;

/**
 * Reads the JSON object in a model response. The model may surround it with prose or a
 * code fence, so the outermost braces are used.
 */
public final class ResponseParser {

    private final JsonMapper mapper;

    public ResponseParser(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public <T> ParseResult<T> parse(LLMResponse response, Class<T> type) {
        if (type == String.class) {
            String text = response.text().strip();
            return text.isEmpty() ? ParseResult.failed("The response was empty.") : ParseResult.ok(type.cast(text));
        }
        String text = response.text();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return ParseResult.failed("The response did not contain a JSON object.");
        }
        try {
            T value = mapper.fromJson(text.substring(start, end + 1), type);
            return value == null ? ParseResult.failed("The JSON object was empty.") : ParseResult.ok(value);
        } catch (IOException e) {
            return ParseResult.failed("The JSON did not match the required format: " + firstLine(e.getMessage()));
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
