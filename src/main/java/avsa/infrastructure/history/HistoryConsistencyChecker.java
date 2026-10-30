package avsa.infrastructure.history;

import avsa.domain.ConsistencyReport;
import avsa.domain.ConsistencyWarning;
import avsa.domain.LienStatus;
import avsa.domain.OdometerReading;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import avsa.domain.WarningCode;
import java.util.ArrayList;
import java.util.List;

/** Checks an imported history against the vehicle profile (F04). */
public final class HistoryConsistencyChecker {

    public ConsistencyReport check(VehicleHistory history, VehicleProfile profile) {
        List<ConsistencyWarning> warnings = new ArrayList<>();
        if (history.vin().isPresent() && profile.vin().isPresent()
                && !history.vin().get().equalsIgnoreCase(profile.vin().get())) {
            warnings.add(new ConsistencyWarning(WarningCode.VIN_MISMATCH,
                    "The VIN in this report does not match the profile. Confirm it is the same vehicle.", true));
        }
        for (OdometerReading reading : history.odometerReadings()) {
            if (reading.odometerKm() > profile.odometerKm()) {
                warnings.add(new ConsistencyWarning(WarningCode.ODOMETER_ABOVE_PROFILE,
                        "A recorded odometer reading (" + reading.odometerKm() + " km) is above the profile's "
                                + profile.odometerKm() + " km. Check for a data-entry error.", false));
                break;
            }
        }
        if (history.lienStatus() == LienStatus.OUTSTANDING) {
            warnings.add(new ConsistencyWarning(WarningCode.OUTSTANDING_LIEN,
                    "A lien is registered against this vehicle. Resolve it or get it discharged before the sale.",
                    false));
        }
        return new ConsistencyReport(warnings);
    }
}
