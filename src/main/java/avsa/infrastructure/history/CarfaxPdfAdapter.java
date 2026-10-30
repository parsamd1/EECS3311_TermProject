package avsa.infrastructure.history;

import avsa.domain.AccidentRecord;
import avsa.domain.HistorySourceType;
import avsa.domain.LienStatus;
import avsa.domain.OdometerReading;
import avsa.domain.VehicleHistory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter pattern: presents a CARFAX Canada report (PDF or plain text) through
 * {@link HistoryImporter}. Known sections are parsed with fixed rules; anything else
 * stays unknown. Only vehicle facts are read: the lien section's debtor name and
 * address are deliberately never extracted.
 */
public final class CarfaxPdfAdapter implements HistoryImporter {

    private static final Pattern VIN_LINE = Pattern.compile("(?m)^\\s*(?:VIN\\s+)?([A-HJ-NPR-Z0-9]{17})\\s*$");
    private static final Pattern SERVICE_COUNT = Pattern.compile("(?i)(\\d+)\\s+Service\\s+Records?\\s+Found");
    private static final Pattern NO_SERVICE = Pattern.compile("(?i)No\\s+Service\\s+Records?\\s+Found");
    private static final Pattern OWNER_EVENT = Pattern.compile("(?i)(?:First|New)\\s+Owner\\s+Reported");
    private static final Pattern NO_ACCIDENTS =
            Pattern.compile("(?i)No\\s+Accident/Damage\\s+Records?\\s+Found|There\\s+are\\s+no\\s+accidents/damage\\s+reported");
    private static final Pattern LIEN_FOUND = Pattern.compile("(?im)^\\s*Lien\\s+Record\\(s\\)\\s+found");
    private static final Pattern NO_LIEN = Pattern.compile("(?i)No\\s+lien\\s+records?\\s+found");
    private static final Pattern PROVINCE = Pattern.compile("In the province of\\s+(.+?)\\s+with");
    private static final Pattern ODOMETER_ROW = Pattern.compile(
            "(?m)^\\s*(\\d{4} (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \\d{1,2})\\s+([\\d,]+)\\s*KM\\s+(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST_ODOMETER = Pattern.compile("(?i)Last Reported Odometer:\\s*([\\d,]+)\\s*KM");
    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("yyyy MMM d", Locale.ENGLISH);

    private final PdfTextExtractor extractor;

    public CarfaxPdfAdapter(PdfTextExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public boolean supports(HistoryInput input) {
        return input.hasFileExtension(".pdf", ".txt");
    }

    @Override
    public VehicleHistory importHistory(HistoryInput input) {
        Path file = input.file().orElseThrow();
        String text = input.hasFileExtension(".pdf") ? extractor.extractText(file) : readText(file);
        if (text.isBlank()) {
            throw new UnsupportedHistoryFormatException(
                    "This file has no readable text (it may be a scanned image). Please enter the history manually.");
        }
        if (!text.contains("CARFAX") || !text.contains("Vehicle History Report")) {
            throw new UnsupportedHistoryFormatException(
                    "This is not a recognized CARFAX report. Please enter the history manually.");
        }
        return parse(text);
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new HistoryFileException("The file could not be read: " + file.getFileName(), e);
        }
    }

    static VehicleHistory parse(String text) {
        Matcher vin = VIN_LINE.matcher(text);
        int ownerEvents = count(OWNER_EVENT, text);
        boolean noAccidents = NO_ACCIDENTS.matcher(text).find();
        return new VehicleHistory(
                vin.find() ? vin.group(1) : null,
                ownerEvents > 0 ? ownerEvents : null,
                serviceCount(text),
                lienStatus(text),
                provinces(text),
                HistorySourceType.CARFAX_PDF,
                noAccidents,
                List.<AccidentRecord>of(),
                odometerReadings(text));
    }

    private static int count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static Integer serviceCount(String text) {
        Matcher m = SERVICE_COUNT.matcher(text);
        if (m.find()) {
            return Integer.valueOf(m.group(1));
        }
        return NO_SERVICE.matcher(text).find() ? Integer.valueOf(0) : null;
    }

    private static LienStatus lienStatus(String text) {
        if (LIEN_FOUND.matcher(text).find()) {
            return LienStatus.OUTSTANDING;
        }
        return NO_LIEN.matcher(text).find() ? LienStatus.NONE : LienStatus.UNKNOWN;
    }

    private static List<String> provinces(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = PROVINCE.matcher(text);
        while (m.find()) {
            found.add(m.group(1).strip());
        }
        return new ArrayList<>(found);
    }

    private static List<OdometerReading> odometerReadings(String text) {
        List<OdometerReading> readings = new ArrayList<>();
        Matcher row = ODOMETER_ROW.matcher(text);
        while (row.find()) {
            try {
                readings.add(new OdometerReading(LocalDate.parse(row.group(1), ROW_DATE), km(row.group(2)),
                        row.group(3).strip()));
            } catch (DateTimeParseException e) {
                // An unreadable row stays unknown rather than being guessed.
            }
        }
        if (readings.isEmpty()) {
            Matcher last = LAST_ODOMETER.matcher(text);
            if (last.find()) {
                readings.add(new OdometerReading(null, km(last.group(1)), "CARFAX last reported"));
            }
        }
        return readings;
    }

    private static int km(String digits) {
        return Integer.parseInt(digits.replace(",", ""));
    }
}
