package avsa.infrastructure.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.BodyStyle;
import avsa.domain.ConsistencyWarning;
import avsa.domain.FuelType;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.Location;
import avsa.domain.Transmission;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import avsa.domain.WarningCode;
import avsa.infrastructure.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryImportTest {

    private static final String SAMPLE_VIN = "1M8GDM9AXKP042788";

    @TempDir
    Path dir;

    private final HistoryImportService service = new HistoryImportService(
            List.of(new CarfaxPdfAdapter(new PdfTextExtractor()), new JsonHistoryAdapter(new JsonMapper()),
                    new ManualHistoryImporter()),
            new HistoryConsistencyChecker());

    private static VehicleProfile.Builder profile() {
        return VehicleProfile.builder().make("Example").model("Model").year(2020).trim("Base")
                .bodyStyle(BodyStyle.SUV).odometerKm(60_000).transmission(Transmission.AUTOMATIC)
                .fuelType(FuelType.GASOLINE).location(new Location("Toronto", "ON", 43.65, -79.38));
    }

    private static String sampleText() throws IOException {
        try (InputStream in = HistoryImportTest.class.getResourceAsStream("/history/carfax-sample.txt")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path pdf(String... lines) throws IOException {
        Path file = dir.resolve("report.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (lines.length > 0) {
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    content.setLeading(12);
                    content.newLineAtOffset(40, 720);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    @Test
    void parsesAllKnownCarfaxSections() throws IOException {
        VehicleHistory h = CarfaxPdfAdapter.parse(sampleText());
        assertEquals(SAMPLE_VIN, h.vin().orElseThrow());
        assertEquals(2, h.ownerCount().orElseThrow(), "first owner + one new owner");
        assertEquals(3, h.serviceRecordCount().orElseThrow());
        assertEquals(LienStatus.OUTSTANDING, h.lienStatus());
        assertEquals(List.of("Quebec", "Ontario"), h.registrationProvinces(), "oldest to newest");
        assertEquals(HistorySourceType.CARFAX_PDF, h.sourceType());
        assertTrue(h.accidentsKnown());
        assertFalse(h.hasAccidents());
        assertEquals(List.of(40_250, 52_100), h.odometerReadings().stream().map(r -> r.odometerKm()).toList());
        assertEquals(LocalDate.of(2024, 6, 1), h.odometerReadings().get(0).date());
        assertEquals(List.of("No reported accidents"), h.trustBadges());
        assertTrue(h.fieldsToConfirm().isEmpty());
    }

    @Test
    void anythingUnreadableStaysUnknownAndIsMarkedForConfirmation() {
        VehicleHistory h = CarfaxPdfAdapter.parse("CARFAX Canada Vehicle History Report\nAccident/Damage record details");
        assertTrue(h.vin().isEmpty());
        assertTrue(h.ownerCount().isEmpty());
        assertTrue(h.serviceRecordCount().isEmpty());
        assertEquals(LienStatus.UNKNOWN, h.lienStatus());
        assertFalse(h.accidentsKnown(), "accident rows are not guessed");
        assertEquals(List.of("vin", "ownerCount", "serviceRecordCount", "lienStatus", "accidents"), h.fieldsToConfirm());
        assertTrue(h.trustBadges().isEmpty());
    }

    @Test
    void noLienAndNoServiceAreReadAsFacts() {
        VehicleHistory h = CarfaxPdfAdapter.parse("No lien records found in Ontario.\nNo Service Records Found");
        assertEquals(LienStatus.NONE, h.lienStatus());
        assertEquals(0, h.serviceRecordCount().orElseThrow());
    }

    @Test
    void lastReportedOdometerIsUsedWhenThereAreNoDatedRows() {
        VehicleHistory h = CarfaxPdfAdapter.parse("Last Reported Odometer: 61,000 KM");
        assertEquals(61_000, h.odometerReadings().get(0).odometerKm());
    }

    @Test
    void importsARealPdfEndToEnd() throws IOException {
        Path file = pdf("CARFAX Canada Vehicle History Report", SAMPLE_VIN, "No Accident/Damage Records Found",
                "1 Service Record Found", "First Owner Reported");
        HistoryImportResult result = service.importFrom(HistoryInput.fromFile(file), profile().build());
        assertFalse(result.needsManualEntry());
        assertEquals(SAMPLE_VIN, result.history().vin().orElseThrow());
        assertEquals(List.of("Single owner", "No reported accidents"), result.history().trustBadges());
    }

    @Test
    void scannedPdfWithoutTextAsksForManualEntry() throws IOException {
        HistoryImportResult result = service.importFrom(HistoryInput.fromFile(pdf()), profile().build());
        assertTrue(result.needsManualEntry());
        assertTrue(result.reason().orElseThrow().contains("no readable text"));
    }

    @Test
    void unrecognizedDocumentAsksForManualEntry() throws IOException {
        HistoryImportResult result = service.importFrom(HistoryInput.fromFile(pdf("A restaurant menu")), profile().build());
        assertTrue(result.needsManualEntry());
        assertTrue(result.reason().orElseThrow().contains("not a recognized"));
    }

    @Test
    void corruptFileIsAnErrorAndNothingIsImported() throws IOException {
        Path garbage = Files.writeString(dir.resolve("broken.pdf"), "this is not a pdf");
        assertThrows(HistoryFileException.class,
                () -> service.importFrom(HistoryInput.fromFile(garbage), profile().build()));
    }

    @Test
    void unsupportedExtensionAsksForManualEntry() throws IOException {
        Path doc = Files.writeString(dir.resolve("report.docx"), "x");
        assertTrue(service.importFrom(HistoryInput.fromFile(doc), profile().build()).needsManualEntry());
    }

    @Test
    void importsJsonAndLeavesMissingFieldsUnknown() throws IOException {
        Path json = Files.writeString(dir.resolve("history.json"), """
                {"vin": "%s", "ownerCount": 1, "lienStatus": "NONE",
                 "registrationProvinces": ["Ontario"],
                 "accidents": [{"date": "2024-02-03", "severity": "MINOR", "description": "Rear bumper"}]}
                """.formatted(SAMPLE_VIN));
        VehicleHistory h = service.importFrom(HistoryInput.fromFile(json), profile().build()).history();
        assertEquals(HistorySourceType.JSON, h.sourceType());
        assertEquals(1, h.accidents().size());
        assertTrue(h.accidentsKnown());
        assertTrue(h.serviceRecordCount().isEmpty());
    }

    @Test
    void malformedJsonIsAnError() throws IOException {
        Path json = Files.writeString(dir.resolve("bad.json"), "{not json");
        assertThrows(HistoryFileException.class,
                () -> service.importFrom(HistoryInput.fromFile(json), profile().build()));
    }

    @Test
    void manualEntryIsMarkedAsManual() {
        VehicleHistory typed = new VehicleHistory(null, 2, 1, LienStatus.NONE, List.of("Ontario"), null, true,
                List.of(), List.of());
        VehicleHistory h = service.importFrom(HistoryInput.manual(typed), profile().build()).history();
        assertEquals(HistorySourceType.MANUAL, h.sourceType());
    }

    private static List<WarningCode> codes(List<ConsistencyWarning> warnings) {
        return warnings.stream().map(ConsistencyWarning::code).toList();
    }

    @Test
    void consistencyWarningsMatchTheF04Rules() throws IOException {
        VehicleHistory h = CarfaxPdfAdapter.parse(sampleText());
        HistoryConsistencyChecker checker = new HistoryConsistencyChecker();

        var matching = checker.check(h, profile().vin(SAMPLE_VIN).build());
        assertEquals(List.of(WarningCode.OUTSTANDING_LIEN), codes(matching.warnings()));
        assertFalse(matching.hasBlockingWarning());

        var otherVin = checker.check(h, profile().vin("11111111111111111").build());
        assertTrue(codes(otherVin.warnings()).contains(WarningCode.VIN_MISMATCH));
        assertTrue(otherVin.hasBlockingWarning());

        var lowOdometer = checker.check(h, profile().odometerKm(50_000).build());
        assertTrue(codes(lowOdometer.warnings()).contains(WarningCode.ODOMETER_ABOVE_PROFILE));
    }
}
