package avsa.infrastructure.history;

import avsa.domain.ConsistencyReport;
import avsa.domain.VehicleHistory;
import java.util.Optional;

/** Either a parsed history with its consistency report, or a request for manual entry. */
public record HistoryImportResult(VehicleHistory history, ConsistencyReport report, String manualEntryReason) {

    public static HistoryImportResult imported(VehicleHistory history, ConsistencyReport report) {
        return new HistoryImportResult(history, report, null);
    }

    public static HistoryImportResult manualEntryRequired(String reason) {
        return new HistoryImportResult(null, null, reason);
    }

    public boolean needsManualEntry() {
        return history == null;
    }

    public Optional<String> reason() {
        return Optional.ofNullable(manualEntryReason);
    }
}
