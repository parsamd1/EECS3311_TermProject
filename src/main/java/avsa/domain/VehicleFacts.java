package avsa.domain;

import java.util.List;
import java.util.Objects;

/**
 * The facts a listing may state, and the ones it must disclose. This is what the LLM is
 * given, so it leaves out the VIN and anything else that identifies the seller.
 *
 * @param ownerCount       null when unknown
 * @param accidentsKnown   false when the accident history has not been confirmed
 * @param accidentsReported number of reported accidents; meaningful only when known
 */
public record VehicleFacts(int year, String make, String model, String trim, BodyStyle bodyStyle, int odometerKm,
                           Transmission transmission, FuelType fuelType, String city, String province,
                           List<String> features, List<MaintenanceRecord> maintenanceRecords, List<Defect> defects,
                           Integer ownerCount, boolean accidentsKnown, int accidentsReported, boolean lienOutstanding,
                           Money price) {

    public VehicleFacts {
        Objects.requireNonNull(price, "price");
        features = List.copyOf(features);
        maintenanceRecords = List.copyOf(maintenanceRecords);
        defects = List.copyOf(defects);
    }

    /**
     * @param history may be null, in which case accidents count as unconfirmed
     * @param price   the active asking price
     */
    public static VehicleFacts from(VehicleProfile profile, VehicleHistory history, Money price) {
        Location location = profile.location();
        return new VehicleFacts(profile.year(), profile.make(), profile.model(), profile.trim(), profile.bodyStyle(),
                profile.odometerKm(), profile.transmission(), profile.fuelType(),
                location == null ? "" : location.city(), location == null ? "" : location.province(),
                profile.features(), profile.maintenanceRecords(), profile.condition().defects(),
                history == null ? null : history.ownerCount().orElse(null),
                history != null && history.accidentsKnown(), history == null ? 0 : history.accidents().size(),
                history != null && history.lienStatus() == LienStatus.OUTSTANDING, price);
    }

    /** "2022 Mazda CX-5 GS" */
    public String vehicleName() {
        String name = year + " " + make + " " + model;
        return trim.isBlank() ? name : name + " " + trim;
    }
}
