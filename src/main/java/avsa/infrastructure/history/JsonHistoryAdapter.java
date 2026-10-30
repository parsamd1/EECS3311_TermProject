package avsa.infrastructure.history;

import avsa.domain.AccidentRecord;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.OdometerReading;
import avsa.domain.VehicleHistory;
import avsa.infrastructure.JsonMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Adapter pattern: presents a JSON history file through {@link HistoryImporter}. */
public final class JsonHistoryAdapter implements HistoryImporter {

    /** The file's layout. Missing fields stay unknown. */
    record HistoryDocument(String vin, Integer ownerCount, Integer serviceRecordCount, LienStatus lienStatus,
                           List<String> registrationProvinces, Boolean accidentsKnown,
                           List<AccidentRecord> accidents, List<OdometerReading> odometerReadings) {
    }

    private final JsonMapper mapper;

    public JsonHistoryAdapter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(HistoryInput input) {
        return input.hasFileExtension(".json");
    }

    @Override
    public VehicleHistory importHistory(HistoryInput input) {
        Path file = input.file().orElseThrow();
        HistoryDocument doc;
        try {
            doc = mapper.read(file, HistoryDocument.class);
        } catch (IOException e) {
            throw new HistoryFileException("The JSON file could not be read: " + file.getFileName(), e);
        }
        List<AccidentRecord> accidents = doc.accidents() == null ? List.of() : doc.accidents();
        boolean accidentsKnown = doc.accidentsKnown() != null ? doc.accidentsKnown() : !accidents.isEmpty();
        return new VehicleHistory(doc.vin(), doc.ownerCount(), doc.serviceRecordCount(), doc.lienStatus(),
                doc.registrationProvinces() == null ? List.of() : doc.registrationProvinces(),
                HistorySourceType.JSON, accidentsKnown, accidents,
                doc.odometerReadings() == null ? List.of() : doc.odometerReadings());
    }
}
