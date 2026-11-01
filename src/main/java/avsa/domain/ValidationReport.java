package avsa.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The result of checking a listing. */
public record ValidationReport(Instant checkedAt, List<Violation> violations) {

    public ValidationReport {
        Objects.requireNonNull(checkedAt, "checkedAt");
        violations = List.copyOf(violations);
    }

    public boolean isValid() {
        return violations.isEmpty();
    }
}
