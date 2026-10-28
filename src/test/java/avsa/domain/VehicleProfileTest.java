package avsa.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VehicleProfileTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private static List<String> fields(List<ValidationError> errors) {
        return errors.stream().map(ValidationError::field).toList();
    }

    @Test
    void completeProfileHasNoErrors() {
        assertTrue(TestVehicles.civic().build().validate(TODAY).isEmpty());
    }

    @Test
    void yearOutsideRangeIsBlocking() {
        List<ValidationError> tooOld = TestVehicles.civic().year(1979).build().validate(TODAY);
        List<ValidationError> tooNew = TestVehicles.civic().year(2028).build().validate(TODAY);
        assertEquals(List.of("year"), fields(tooOld));
        assertTrue(VehicleProfile.hasBlockingErrors(tooOld));
        assertEquals(List.of("year"), fields(tooNew));
        assertTrue(TestVehicles.civic().year(2027).build().validate(TODAY).isEmpty(),
                "next model year is allowed");
    }

    @Test
    void odometerOutsideRangeIsBlocking() {
        assertEquals(List.of("odometerKm"), fields(TestVehicles.civic().odometerKm(-1).build().validate(TODAY)));
        assertEquals(List.of("odometerKm"),
                fields(TestVehicles.civic().odometerKm(1_000_000).build().validate(TODAY)));
    }

    @Test
    void missingRequiredFieldsAreBlocking() {
        VehicleProfile p = TestVehicles.civic().make(" ").bodyStyle(null).location(null).build();
        assertEquals(List.of("make", "bodyStyle", "location"), fields(p.validate(TODAY)));
    }

    @Test
    void validVinIsAccepted() {
        assertTrue(VehicleProfile.isValidVin("1M8GDM9AXKP042788"));
        assertTrue(TestVehicles.civic().vin("1m8gdm9axkp042788").build().validate(TODAY).isEmpty());
    }

    @Test
    void invalidVinIsOnlyAWarning() {
        assertFalse(VehicleProfile.isValidVin("1M8GDM9A1KP042788"), "wrong check digit");
        assertFalse(VehicleProfile.isValidVin("1M8GDM9AXKP04278"), "16 characters");
        assertFalse(VehicleProfile.isValidVin("1M8GDM9AXKP0427O8"), "contains O");
        List<ValidationError> errors = TestVehicles.civic().vin("BADVIN").build().validate(TODAY);
        assertEquals(List.of("vin"), fields(errors));
        assertFalse(VehicleProfile.hasBlockingErrors(errors));
    }

    @Test
    void customModelAndGradeConflictAreWarnings() {
        ConditionReport excellentButDamaged = new ConditionReport(ConditionGrade.EXCELLENT,
                List.of(new Defect(DefectType.DENT, "rear bumper", Severity.SEVERE, "", null)));
        List<ValidationError> errors = TestVehicles.civic().customModel(true)
                .condition(excellentButDamaged).build().validate(TODAY);
        assertEquals(List.of("model", "overallGrade"), fields(errors));
        assertFalse(VehicleProfile.hasBlockingErrors(errors));
    }

    @Test
    void segmentFollowsFuelMakeAndBody() {
        assertEquals(Segment.ECONOMY, TestVehicles.civic().build().segment());
        assertEquals(Segment.EV, TestVehicles.civic().fuelType(FuelType.ELECTRIC).build().segment());
        assertEquals(Segment.LUXURY, TestVehicles.civic().make("BMW").build().segment());
        assertEquals(Segment.TRUCK, TestVehicles.civic().bodyStyle(BodyStyle.TRUCK).build().segment());
        assertEquals(Segment.SUV, TestVehicles.civic().bodyStyle(BodyStyle.SUV).build().segment());
    }

    @Test
    void ageIsWholeYearsAndNeverNegative() {
        assertEquals(6, TestVehicles.civic().build().ageInYears(TODAY));
        assertEquals(0, TestVehicles.civic().year(2027).build().ageInYears(TODAY));
    }

    @Test
    void sameVehicleUsesVinWhenBothHaveOne() {
        VehicleProfile a = TestVehicles.civic().vin("1M8GDM9AXKP042788").build();
        VehicleProfile b = TestVehicles.civic().vin("1M8GDM9AXKP042788").odometerKm(90_000).build();
        VehicleProfile c = TestVehicles.civic().vin("11111111111111111").build();
        assertTrue(a.sameVehicleAs(b));
        assertFalse(a.sameVehicleAs(c));
        assertTrue(TestVehicles.civic().build().sameVehicleAs(TestVehicles.civic().trim("ex").build()));
    }
}
