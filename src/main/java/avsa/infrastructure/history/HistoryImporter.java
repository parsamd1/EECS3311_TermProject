package avsa.infrastructure.history;

import avsa.domain.VehicleHistory;

/** Adapter pattern target: the one interface the application uses to obtain a history, whatever its source. */
public interface HistoryImporter {

    boolean supports(HistoryInput input);

    /**
     * @throws UnsupportedHistoryFormatException the content has no readable text or an unknown format
     * @throws HistoryFileException the file could not be read at all
     */
    VehicleHistory importHistory(HistoryInput input);
}
