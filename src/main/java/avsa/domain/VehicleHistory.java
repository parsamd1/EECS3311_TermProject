package avsa.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The vehicle's reported history (F04). A value that could not be read stays
 * unknown (null, or {@code accidentsKnown == false}) and is never guessed.
 */
public final class VehicleHistory {

    private final String vin;
    private final Integer ownerCount;
    private final Integer serviceRecordCount;
    private final LienStatus lienStatus;
    private final List<String> registrationProvinces;
    private final HistorySourceType sourceType;
    private final boolean accidentsKnown;
    private final List<AccidentRecord> accidents;
    private final List<OdometerReading> odometerReadings;

    public VehicleHistory(String vin, Integer ownerCount, Integer serviceRecordCount, LienStatus lienStatus,
                          List<String> registrationProvinces, HistorySourceType sourceType, boolean accidentsKnown,
                          List<AccidentRecord> accidents, List<OdometerReading> odometerReadings) {
        this.vin = vin == null || vin.isBlank() ? null : vin.strip();
        this.ownerCount = ownerCount;
        this.serviceRecordCount = serviceRecordCount;
        this.lienStatus = lienStatus == null ? LienStatus.UNKNOWN : lienStatus;
        this.registrationProvinces = List.copyOf(registrationProvinces);
        this.sourceType = sourceType;
        this.accidentsKnown = accidentsKnown;
        this.accidents = List.copyOf(accidents);
        this.odometerReadings = List.copyOf(odometerReadings);
    }

    public Optional<String> vin() { return Optional.ofNullable(vin); }
    public Optional<Integer> ownerCount() { return Optional.ofNullable(ownerCount); }
    public Optional<Integer> serviceRecordCount() { return Optional.ofNullable(serviceRecordCount); }
    public LienStatus lienStatus() { return lienStatus; }
    public List<String> registrationProvinces() { return registrationProvinces; }
    public HistorySourceType sourceType() { return sourceType; }
    public boolean accidentsKnown() { return accidentsKnown; }
    public List<AccidentRecord> accidents() { return accidents; }
    public List<OdometerReading> odometerReadings() { return odometerReadings; }

    public boolean hasAccidents() {
        return !accidents.isEmpty();
    }

    /** Fields the seller should confirm because they could not be read. */
    public List<String> fieldsToConfirm() {
        List<String> fields = new ArrayList<>();
        if (vin == null) {
            fields.add("vin");
        }
        if (ownerCount == null) {
            fields.add("ownerCount");
        }
        if (serviceRecordCount == null) {
            fields.add("serviceRecordCount");
        }
        if (lienStatus == LienStatus.UNKNOWN) {
            fields.add("lienStatus");
        }
        if (!accidentsKnown) {
            fields.add("accidents");
        }
        return fields;
    }

    /** Positive facts worth showing to the seller (and later to buyers). */
    public List<String> trustBadges() {
        List<String> badges = new ArrayList<>();
        if (ownerCount != null && ownerCount == 1) {
            badges.add("Single owner");
        }
        if (accidentsKnown && accidents.isEmpty()) {
            badges.add("No reported accidents");
        }
        return badges;
    }
}
