package avsa.infrastructure.history;

import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import java.util.List;

/** Picks the importer for the input, runs it, and checks the result against the profile (F04). */
public final class HistoryImportService {

    private final List<HistoryImporter> importers;
    private final HistoryConsistencyChecker checker;

    public HistoryImportService(List<HistoryImporter> importers, HistoryConsistencyChecker checker) {
        this.importers = List.copyOf(importers);
        this.checker = checker;
    }

    /**
     * @throws HistoryFileException the file is corrupt or unreadable; nothing is imported
     */
    public HistoryImportResult importFrom(HistoryInput input, VehicleProfile profile) {
        for (HistoryImporter importer : importers) {
            if (importer.supports(input)) {
                try {
                    VehicleHistory history = importer.importHistory(input);
                    return HistoryImportResult.imported(history, checker.check(history, profile));
                } catch (UnsupportedHistoryFormatException e) {
                    return HistoryImportResult.manualEntryRequired(e.getMessage());
                }
            }
        }
        return HistoryImportResult.manualEntryRequired(
                "This file type is not supported. Use a PDF, TXT or JSON report, or enter the history manually.");
    }
}
