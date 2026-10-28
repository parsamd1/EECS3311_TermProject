package avsa.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything known about the vehicle being sold (F01). Every other feature
 * reads from this profile. Instances are immutable; use {@link #builder()}.
 */
public final class VehicleProfile {

    public static final int MIN_YEAR = 1980;
    public static final int MAX_ODOMETER_KM = 999_999;

    private static final Set<String> LUXURY_MAKES = Set.of(
            "ACURA", "AUDI", "BMW", "CADILLAC", "GENESIS", "INFINITI", "JAGUAR", "LAND ROVER",
            "LEXUS", "LINCOLN", "MERCEDES-BENZ", "PORSCHE", "VOLVO");

    private final UUID id;
    private final String make;
    private final String model;
    private final int year;
    private final String trim;
    private final BodyStyle bodyStyle;
    private final int odometerKm;
    private final Transmission transmission;
    private final FuelType fuelType;
    private final String vin;
    private final List<String> features;
    private final boolean customModel;
    private final Location location;
    private final List<MaintenanceRecord> maintenanceRecords;
    private final ConditionReport condition;

    private VehicleProfile(Builder b) {
        this.id = b.id != null ? b.id : UUID.randomUUID();
        this.make = clean(b.make);
        this.model = clean(b.model);
        this.year = b.year;
        this.trim = clean(b.trim);
        this.bodyStyle = b.bodyStyle;
        this.odometerKm = b.odometerKm;
        this.transmission = b.transmission;
        this.fuelType = b.fuelType;
        this.vin = b.vin == null || b.vin.isBlank() ? null : b.vin.strip().toUpperCase(Locale.ROOT);
        this.features = List.copyOf(b.features);
        this.customModel = b.customModel;
        this.location = b.location;
        this.maintenanceRecords = List.copyOf(b.maintenanceRecords);
        this.condition = b.condition != null ? b.condition : new ConditionReport(null, List.of());
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID id() { return id; }
    public String make() { return make; }
    public String model() { return model; }
    public int year() { return year; }
    public String trim() { return trim; }
    public BodyStyle bodyStyle() { return bodyStyle; }
    public int odometerKm() { return odometerKm; }
    public Transmission transmission() { return transmission; }
    public FuelType fuelType() { return fuelType; }
    public Optional<String> vin() { return Optional.ofNullable(vin); }
    public List<String> features() { return features; }
    public boolean customModel() { return customModel; }
    public Location location() { return location; }
    public List<MaintenanceRecord> maintenanceRecords() { return maintenanceRecords; }
    public ConditionReport condition() { return condition; }

    public int ageInYears(LocalDate today) {
        return Math.max(0, today.getYear() - year);
    }

    /** Market segment, which selects the depreciation rates (F06). */
    public Segment segment() {
        if (fuelType == FuelType.ELECTRIC) {
            return Segment.EV;
        }
        if (LUXURY_MAKES.contains(make.toUpperCase(Locale.ROOT))) {
            return Segment.LUXURY;
        }
        if (bodyStyle == BodyStyle.TRUCK) {
            return Segment.TRUCK;
        }
        if (bodyStyle == BodyStyle.SUV) {
            return Segment.SUV;
        }
        return Segment.ECONOMY;
    }

    public List<ValidationError> validate() {
        return validate(LocalDate.now());
    }

    /** Checks the F01 input rules. Blocking errors must be fixed before saving. */
    public List<ValidationError> validate(LocalDate today) {
        List<ValidationError> errors = new ArrayList<>();
        if (make.isEmpty()) {
            errors.add(ValidationError.error("make", "Make is required."));
        }
        if (model.isEmpty()) {
            errors.add(ValidationError.error("model", "Model is required."));
        }
        int maxYear = today.getYear() + 1;
        if (year < MIN_YEAR || year > maxYear) {
            errors.add(ValidationError.error("year",
                    "Year must be between " + MIN_YEAR + " and " + maxYear + "."));
        }
        if (odometerKm < 0 || odometerKm > MAX_ODOMETER_KM) {
            errors.add(ValidationError.error("odometerKm",
                    "Odometer must be between 0 and " + MAX_ODOMETER_KM + " km."));
        }
        if (bodyStyle == null) {
            errors.add(ValidationError.error("bodyStyle", "Body style is required."));
        }
        if (transmission == null) {
            errors.add(ValidationError.error("transmission", "Transmission is required."));
        }
        if (fuelType == null) {
            errors.add(ValidationError.error("fuelType", "Fuel type is required."));
        }
        if (location == null) {
            errors.add(ValidationError.error("location", "City and province are required."));
        }
        if (vin != null && !isValidVin(vin)) {
            errors.add(ValidationError.warning("vin",
                    "The VIN does not look valid (17 characters, no I, O or Q, correct check digit)."));
        }
        if (customModel) {
            errors.add(ValidationError.warning("model",
                    "This make/model is not in the catalog; fewer comparable listings may be found."));
        }
        if (condition.gradeConflictsWithDefects()) {
            errors.add(ValidationError.warning("overallGrade",
                    "The chosen grade is better than the recorded defects suggest ("
                            + condition.suggestedGrade() + "). Please confirm it."));
        }
        return errors;
    }

    public static boolean hasBlockingErrors(List<ValidationError> errors) {
        return errors.stream().anyMatch(ValidationError::blocking);
    }

    /** North American VIN check: 17 characters, no I/O/Q, and a valid check digit (position 9). */
    static boolean isValidVin(String vin) {
        if (vin.length() != 17 || !vin.matches("[A-HJ-NPR-Z0-9]{17}")) {
            return false;
        }
        int[] weights = {8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += transliterate(vin.charAt(i)) * weights[i];
        }
        int remainder = sum % 11;
        char expected = remainder == 10 ? 'X' : (char) ('0' + remainder);
        return vin.charAt(8) == expected;
    }

    private static int transliterate(char c) {
        if (Character.isDigit(c)) {
            return c - '0';
        }
        String values = "12345678_12345_7_923456789";
        return values.charAt(c - 'A') - '0';
    }

    /** True when the other profile describes the same physical vehicle. */
    public boolean sameVehicleAs(VehicleProfile other) {
        if (vin != null && other.vin != null) {
            return vin.equals(other.vin);
        }
        return make.equalsIgnoreCase(other.make) && model.equalsIgnoreCase(other.model)
                && year == other.year && Objects.equals(trim.toLowerCase(Locale.ROOT), other.trim.toLowerCase(Locale.ROOT));
    }

    public static final class Builder {
        private UUID id;
        private String make;
        private String model;
        private int year;
        private String trim;
        private BodyStyle bodyStyle;
        private int odometerKm;
        private Transmission transmission;
        private FuelType fuelType;
        private String vin;
        private List<String> features = List.of();
        private boolean customModel;
        private Location location;
        private List<MaintenanceRecord> maintenanceRecords = List.of();
        private ConditionReport condition;

        private Builder() {
        }

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder make(String make) { this.make = make; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder year(int year) { this.year = year; return this; }
        public Builder trim(String trim) { this.trim = trim; return this; }
        public Builder bodyStyle(BodyStyle bodyStyle) { this.bodyStyle = bodyStyle; return this; }
        public Builder odometerKm(int odometerKm) { this.odometerKm = odometerKm; return this; }
        public Builder transmission(Transmission transmission) { this.transmission = transmission; return this; }
        public Builder fuelType(FuelType fuelType) { this.fuelType = fuelType; return this; }
        public Builder vin(String vin) { this.vin = vin; return this; }
        public Builder features(List<String> features) { this.features = features; return this; }
        public Builder customModel(boolean customModel) { this.customModel = customModel; return this; }
        public Builder location(Location location) { this.location = location; return this; }
        public Builder maintenanceRecords(List<MaintenanceRecord> records) { this.maintenanceRecords = records; return this; }
        public Builder condition(ConditionReport condition) { this.condition = condition; return this; }

        public VehicleProfile build() {
            return new VehicleProfile(this);
        }
    }
}
