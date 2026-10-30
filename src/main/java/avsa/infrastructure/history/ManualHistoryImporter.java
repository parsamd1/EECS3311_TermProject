package avsa.infrastructure.history;

import avsa.domain.HistorySourceType;
import avsa.domain.VehicleHistory;

/** Accepts values from the manual entry form. It adapts no external class, so it is not an Adapter. */
public final class ManualHistoryImporter implements HistoryImporter {

    @Override
    public boolean supports(HistoryInput input) {
        return input.manualEntry().isPresent();
    }

    @Override
    public VehicleHistory importHistory(HistoryInput input) {
        VehicleHistory h = input.manualEntry().orElseThrow();
        return new VehicleHistory(h.vin().orElse(null), h.ownerCount().orElse(null),
                h.serviceRecordCount().orElse(null), h.lienStatus(), h.registrationProvinces(),
                HistorySourceType.MANUAL, h.accidentsKnown(), h.accidents(), h.odometerReadings());
    }
}
