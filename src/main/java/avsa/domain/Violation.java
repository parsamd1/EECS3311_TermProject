package avsa.domain;

import java.util.Objects;

/** One broken rule, worded so it can be shown to the seller or sent back to the LLM as feedback. */
public record Violation(ValidationRule rule, String message) {

    public Violation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(message, "message");
    }
}
