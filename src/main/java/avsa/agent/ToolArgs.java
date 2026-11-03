package avsa.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Named arguments for a tool. */
public final class ToolArgs {

    private final Map<String, Object> values;

    private ToolArgs(Map<String, Object> values) {
        this.values = values;
    }

    /** Pairs of name and value; a null value is left out. */
    public static ToolArgs of(Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Tool arguments come in name/value pairs.");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (namesAndValues[i + 1] != null) {
                values.put((String) namesAndValues[i], namesAndValues[i + 1]);
            }
        }
        return new ToolArgs(values);
    }

    /** @throws IllegalArgumentException the argument is missing or has the wrong type */
    public <T> T require(String name, Class<T> type) {
        return optional(name, type).orElseThrow(() -> new IllegalArgumentException("Missing argument: " + name));
    }

    public <T> Optional<T> optional(String name, Class<T> type) {
        Object value = values.get(name);
        if (value != null && !type.isInstance(value)) {
            throw new IllegalArgumentException("Argument " + name + " must be a " + type.getSimpleName());
        }
        return Optional.ofNullable(type.cast(value));
    }

    /** The argument names, for the trace; values may hold personal data so they are not recorded. */
    public String describe() {
        return String.join(", ", values.keySet());
    }
}
