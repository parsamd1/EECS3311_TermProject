package avsa.domain;

import java.util.List;

/** Result of checking a vehicle history against the profile (F04). */
public record ConsistencyReport(List<ConsistencyWarning> warnings) {

    public ConsistencyReport {
        warnings = List.copyOf(warnings);
    }

    public boolean hasBlockingWarning() {
        return warnings.stream().anyMatch(ConsistencyWarning::blocking);
    }
}
