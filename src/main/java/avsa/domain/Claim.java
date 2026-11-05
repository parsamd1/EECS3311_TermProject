package avsa.domain;

import java.util.List;
import java.util.Objects;

/**
 * One statement in a positioning report, with what it is based on: listing ids of comparables,
 * or "metric:" plus a metric name (F05).
 */
public record Claim(ClaimKind kind, String text, List<String> evidenceRefs) {

    public Claim {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        evidenceRefs = List.copyOf(evidenceRefs);
    }
}
