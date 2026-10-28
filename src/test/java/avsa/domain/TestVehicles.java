package avsa.domain;

import java.util.List;

/** Shared test fixtures. */
final class TestVehicles {

    static final Location TORONTO = new Location("Toronto", "ON", 43.6532, -79.3832);

    private TestVehicles() {
    }

    static VehicleProfile.Builder civic() {
        return VehicleProfile.builder()
                .make("Honda").model("Civic").year(2020).trim("EX")
                .bodyStyle(BodyStyle.SEDAN).odometerKm(80_000)
                .transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE)
                .location(TORONTO)
                .condition(ConditionReport.noDefects(null))
                .features(List.of("Heated seats"));
    }
}
