package avsa.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.AccidentRecord;
import avsa.domain.BodyStyle;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.FuelType;
import avsa.domain.GeneratedListing;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.ListingOptions;
import avsa.domain.Location;
import avsa.domain.MaintenanceRecord;
import avsa.domain.Money;
import avsa.domain.Severity;
import avsa.domain.Tone;
import avsa.domain.Transmission;
import avsa.domain.ValidationReport;
import avsa.domain.ValidationRule;
import avsa.domain.VehicleFacts;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingValidatorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private static final Money PRICE = Money.cad(29_500);
    private static final String TITLE = "2022 Mazda CX-5 GS - 49,000 km";
    private static final String BODY = "A well kept 2022 Mazda CX-5 GS with 49,000 km, automatic transmission and winter "
            + "tires, offered at $29,500 and available to view in Toronto.";
    private static final String DISCLOSURE = "Minor scratch on the rear door. The history shows no reported accident.";

    private final ListingValidator validator = new ListingValidator(CLOCK);
    private final TemplateListingWriter writer = new TemplateListingWriter();

    private static VehicleProfile.Builder cx5() {
        return VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS").bodyStyle(BodyStyle.SUV)
                .odometerKm(49_000).transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE)
                .location(new Location("Toronto", "ON", 43.65, -79.38)).features(List.of("Winter tires"))
                .condition(new ConditionReport(null, List.of(new Defect(DefectType.SCRATCH, "rear door", Severity.MINOR,
                        "About 25 cm hairline", null))));
    }

    private static VehicleHistory history(boolean known, List<AccidentRecord> accidents, LienStatus lien) {
        return new VehicleHistory(null, 2, 1, lien, List.of("Ontario"), HistorySourceType.MANUAL, known, accidents,
                List.of());
    }

    private static VehicleFacts facts() {
        return VehicleFacts.from(cx5().build(), history(true, List.of(), LienStatus.NONE), PRICE);
    }

    private static GeneratedListing listing(String title, String description, String disclosure, Money price) {
        return new GeneratedListing(title, description, List.of("Winter tires"), List.of(), disclosure, price, List.of(),
                Tone.PROFESSIONAL, false, false, false, null);
    }

    private ValidationReport check(String title, String description, String disclosure, Money price) {
        return validator.validate(listing(title, description, disclosure, price), facts());
    }

    private static List<ValidationRule> rules(ValidationReport report) {
        return report.violations().stream().map(v -> v.rule()).toList();
    }

    @Test
    void anHonestAccurateListingPasses() {
        ValidationReport report = check(TITLE, BODY, DISCLOSURE, PRICE);
        assertTrue(report.isValid(), () -> report.violations().toString());
        assertEquals(CLOCK.instant(), report.checkedAt());
    }

    @Test
    void everyDefectMustBeDisclosedWithItsLocation() {
        ValidationReport report = check(TITLE, BODY, "Good overall condition.", PRICE);
        assertEquals(List.of(ValidationRule.DEFECT_NOT_DISCLOSED), rules(report));
        assertTrue(report.violations().get(0).message().contains("scratch on the rear door"));
        assertEquals(List.of(ValidationRule.DEFECT_NOT_DISCLOSED), rules(check(TITLE, BODY, "A minor scratch.", PRICE)));
    }

    @Test
    void mentioningADefectOnlyInTheDescriptionDoesNotCount() {
        String body = BODY + " It has a minor scratch on the rear door.";
        assertEquals(List.of(ValidationRule.DEFECT_NOT_DISCLOSED), rules(check(TITLE, body, "Nothing to report.", PRICE)));
    }

    @Test
    void bannedClaimsAreRejectedInAnyPartOfTheListing() {
        assertEquals(List.of(ValidationRule.BANNED_CLAIM), rules(check(TITLE, BODY + " Flawless!", DISCLOSURE, PRICE)));
        assertEquals(List.of(ValidationRule.BANNED_CLAIM), rules(check(TITLE + " like new", BODY, DISCLOSURE, PRICE)));
        assertEquals(List.of(ValidationRule.BANNED_CLAIM), rules(check(TITLE, BODY, DISCLOSURE + " No issues.", PRICE)));
    }

    @Test
    void wholeWordsOnly() {
        assertTrue(check(TITLE, BODY + " The flawlessly named dealer is nearby.", DISCLOSURE, PRICE).isValid());
    }

    @Test
    void accidentFreeIsOnlyAllowedWhenConfirmedClean() {
        String body = BODY + " Accident-free.";
        assertTrue(check(TITLE, body, DISCLOSURE, PRICE).isValid());

        VehicleFacts crashed = VehicleFacts.from(cx5().build(),
                history(true, List.of(new AccidentRecord(null, Severity.MINOR, "Bumper")), LienStatus.NONE), PRICE);
        ValidationReport withAccident = validator.validate(
                listing(TITLE, body, "Minor scratch on the rear door. One reported accident.", PRICE), crashed);
        assertEquals(List.of(ValidationRule.BANNED_CLAIM), rules(withAccident));

        VehicleFacts unknown = VehicleFacts.from(cx5().build(), null, PRICE);
        assertEquals(List.of(ValidationRule.BANNED_CLAIM),
                rules(validator.validate(listing(TITLE, body, DISCLOSURE, PRICE), unknown)));
    }

    @Test
    void aReportedAccidentMustBeDisclosed() {
        VehicleFacts crashed = VehicleFacts.from(cx5().build(),
                history(true, List.of(new AccidentRecord(null, Severity.MINOR, "Bumper")), LienStatus.NONE), PRICE);
        ValidationReport report = validator.validate(
                listing(TITLE, BODY, "Minor scratch on the rear door.", PRICE), crashed);
        assertEquals(List.of(ValidationRule.DEFECT_NOT_DISCLOSED), rules(report));
    }

    @Test
    void wrongFactsAreRejected() {
        assertEquals(List.of(ValidationRule.FACT_MISMATCH), rules(check("Mazda CX-5 for sale", BODY, DISCLOSURE, PRICE)));
        assertEquals(List.of(ValidationRule.FACT_MISMATCH),
                rules(check(TITLE, BODY + " This 2023 Mazda drives well.", DISCLOSURE, PRICE)));
        assertEquals(List.of(ValidationRule.FACT_MISMATCH), rules(check(TITLE, BODY + " Only 30,000 km!", DISCLOSURE, PRICE)));
        assertEquals(List.of(ValidationRule.FACT_MISMATCH), rules(check(TITLE, BODY + " Just 40k km.", DISCLOSURE, PRICE)));
        assertEquals(List.of(ValidationRule.FACT_MISMATCH), rules(check(TITLE, BODY, DISCLOSURE, Money.cad(28_000))));
        assertEquals(List.of(ValidationRule.FACT_MISMATCH), rules(check(TITLE, BODY + " Yours for $27,000.", DISCLOSURE, PRICE)));
    }

    @Test
    void theOdometerMustBeStatedAndSmallAmountsAreNotPrices() {
        assertTrue(check(TITLE, BODY + " New $800 winter tires. Also 49k km.", DISCLOSURE, PRICE).isValid());

        GeneratedListing noKm = new GeneratedListing("2022 Mazda CX-5", "A well kept 2022 Mazda CX-5 GS with automatic "
                + "transmission and winter tires, available to view in Toronto this coming weekend.", List.of(), List.of(),
                DISCLOSURE, PRICE, List.of(), Tone.PROFESSIONAL, false, false, false, null);
        assertEquals(List.of(ValidationRule.FACT_MISMATCH), rules(validator.validate(noKm, facts())));
    }

    @Test
    void lengthMustBeWithinBounds() {
        assertEquals(List.of(ValidationRule.LENGTH_OUT_OF_BOUNDS), rules(check(TITLE, "Runs well. 49,000 km.", DISCLOSURE, PRICE)));
        assertEquals(List.of(ValidationRule.LENGTH_OUT_OF_BOUNDS),
                rules(check(TITLE, BODY + " x".repeat(2000), DISCLOSURE, PRICE)));
    }

    @Test
    void exportableOnlyWhenValidOrOverriddenAndEditsNeedValidatingAgain() {
        GeneratedListing draft = listing(TITLE, BODY, "Nothing to report.", PRICE);
        assertFalse(draft.isExportable(), "not validated yet");

        GeneratedListing failed = draft.withReport(validator.validate(draft, facts()));
        assertFalse(failed.isExportable());
        assertTrue(failed.withOverride().isExportable());

        GeneratedListing edited = failed.withOverride().withEdit(TITLE, BODY, List.of(), List.of(), DISCLOSURE);
        assertTrue(edited.editedByUser());
        assertFalse(edited.isExportable(), "an edit clears the override");
        assertTrue(edited.withReport(validator.validate(edited, facts())).isExportable());
    }

    @Test
    void templateListingsPassValidationInEveryTone() {
        VehicleProfile profile = cx5().maintenanceRecords(List.of(
                new MaintenanceRecord(LocalDate.of(2024, 12, 13), 30_000, "Oil change"))).build();
        VehicleFacts facts = VehicleFacts.from(profile, history(true, List.of(), LienStatus.NONE), PRICE);
        for (Tone tone : Tone.values()) {
            GeneratedListing listing = writer.write(facts, new ListingOptions(tone, true, true, List.of("AWD")));
            ValidationReport report = validator.validate(listing, facts);
            assertTrue(report.isValid(), () -> tone + ": " + report.violations());
            assertTrue(listing.templateFallback());
            assertEquals(tone, listing.tone());
            assertEquals(List.of("2024-12-13: Oil change at 30,000 km"), listing.maintenanceDetails());
            assertTrue(listing.conditionDisclosure().contains("Minor scratch on the rear door (About 25 cm hairline)."));
            assertTrue(listing.seoKeywords().contains("AWD"));
        }
    }

    @Test
    void templateDisclosesHistoryProblemsAndInventsNothing() {
        VehicleProfile plain = VehicleProfile.builder().make("Honda").model("Civic").year(2020).odometerKm(80_000)
                .transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE).bodyStyle(BodyStyle.SEDAN)
                .location(new Location("Ottawa", "ON", 45.42, -75.69)).build();
        VehicleFacts facts = VehicleFacts.from(plain, history(true,
                List.of(new AccidentRecord(null, Severity.MODERATE, "Front")), LienStatus.OUTSTANDING), Money.cad(15_000));
        GeneratedListing listing = writer.write(facts, ListingOptions.defaults());

        assertTrue(listing.highlights().isEmpty());
        assertTrue(listing.maintenanceDetails().isEmpty());
        assertTrue(listing.conditionDisclosure().contains("1 reported accident."));
        assertTrue(listing.conditionDisclosure().contains("outstanding lien"));
        assertTrue(validator.validate(listing, facts).isValid());

        VehicleFacts unknown = VehicleFacts.from(plain, null, Money.cad(15_000));
        String disclosure = writer.write(unknown, ListingOptions.defaults()).conditionDisclosure();
        assertTrue(disclosure.contains("has not been confirmed"));
        assertFalse(writer.write(unknown, ListingOptions.defaults()).description().contains("owner"));
    }

    @Test
    void sectionsCanBeSwitchedOff() {
        GeneratedListing listing = writer.write(facts(), new ListingOptions(Tone.CONCISE, false, false, List.of()));
        assertTrue(listing.highlights().isEmpty());
        assertTrue(listing.maintenanceDetails().isEmpty());
    }

    @Test
    void factsLeaveOutTheVinAndNeedAPrice() {
        VehicleFacts facts = VehicleFacts.from(cx5().vin("1HGCM82633A004352").build(), null, PRICE);
        assertFalse(facts.toString().contains("1HGCM82633A004352"));
        assertThrows(NullPointerException.class, () -> VehicleFacts.from(cx5().build(), null, null));
    }
}
