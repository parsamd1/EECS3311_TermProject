package avsa.domain;

import java.util.Objects;
import java.util.Optional;

/** One recorded defect on the vehicle. {@code repairEstimate} may be null. */
public record Defect(DefectType type, String bodyLocation, Severity severity, String note, Money repairEstimate) {

    public Defect {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        bodyLocation = bodyLocation == null ? "" : bodyLocation.strip();
        note = note == null ? "" : note.strip();
    }

    public Optional<Money> estimate() {
        return Optional.ofNullable(repairEstimate);
    }
}
