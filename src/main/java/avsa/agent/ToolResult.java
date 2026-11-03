package avsa.agent;

import java.util.List;
import java.util.Optional;

/**
 * What a tool produced, or why it failed.
 *
 * @param notes things the user should know, such as an AI step that fell back
 */
public record ToolResult(boolean ok, Object value, String error, List<String> notes) {

    public ToolResult {
        notes = List.copyOf(notes);
    }

    public static ToolResult success(Object value, List<String> notes) {
        return new ToolResult(true, value, null, notes);
    }

    public static ToolResult success(Object value) {
        return success(value, List.of());
    }

    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error, List.of());
    }

    /** @throws IllegalStateException the tool failed, with its error as the message */
    public <T> T valueAs(Class<T> type) {
        if (!ok) {
            throw new IllegalStateException(error);
        }
        return type.cast(value);
    }

    public Optional<String> errorMessage() {
        return Optional.ofNullable(error);
    }
}
