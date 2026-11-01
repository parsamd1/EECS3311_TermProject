package avsa.services;

import avsa.domain.Defect;
import avsa.domain.GeneratedListing;
import avsa.domain.ListingOptions;
import avsa.domain.MaintenanceRecord;
import avsa.domain.Tone;
import avsa.domain.VehicleFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a plain listing from the facts alone, for when the LLM is unavailable (F07).
 * Sections with no data are left out, so nothing is ever invented.
 */
public final class TemplateListingWriter {

    public GeneratedListing write(VehicleFacts facts, ListingOptions options) {
        List<String> highlights = options.includeFeatures() ? facts.features() : List.of();
        List<String> maintenance = options.includeMaintenance()
                ? facts.maintenanceRecords().stream().map(TemplateListingWriter::maintenanceLine).toList()
                : List.of();
        List<String> keywords = new ArrayList<>(List.of(facts.make() + " " + facts.model(), "used " + bodyStyle(facts)));
        keywords.addAll(options.keywords());
        return new GeneratedListing(title(facts), description(facts, options.tone()), highlights, maintenance,
                disclosure(facts), facts.price(), keywords, options.tone(), true, false, false, null);
    }

    private static String title(VehicleFacts facts) {
        return facts.vehicleName() + " - " + String.format(Locale.ROOT, "%,d", facts.odometerKm()) + " km";
    }

    private static String description(VehicleFacts facts, Tone tone) {
        String specs = String.format(Locale.ROOT, "%s with %,d km, %s transmission and a %s engine",
                facts.vehicleName(), facts.odometerKm(), lower(facts.transmission()), lower(facts.fuelType()));
        String where = facts.city().isBlank() ? "" : " Located in " + facts.city() + ", " + facts.province() + ".";
        String owners = facts.ownerCount() == null ? "" : " Vehicle history shows " + facts.ownerCount() + " owner"
                + (facts.ownerCount() == 1 ? "" : "s") + ".";
        return switch (tone) {
            case PROFESSIONAL -> "For sale: " + specs + "." + owners + where
                    + " Please read the condition disclosure below and contact me to arrange a viewing.";
            case FRIENDLY -> "Hi there! I'm selling my " + specs + "." + owners + where
                    + " The condition details are below, and I'm happy to answer questions or set up a test drive.";
            case CONCISE -> specs + "." + owners + where + " See the condition disclosure below.";
        };
    }

    private static String disclosure(VehicleFacts facts) {
        List<String> lines = new ArrayList<>();
        for (Defect defect : facts.defects()) {
            lines.add(defectLine(defect));
        }
        if (!facts.accidentsKnown()) {
            lines.add("Accident history has not been confirmed.");
        } else if (facts.accidentsReported() > 0) {
            lines.add("The vehicle history shows " + facts.accidentsReported() + " reported accident"
                    + (facts.accidentsReported() == 1 ? "" : "s") + ".");
        }
        if (facts.lienOutstanding()) {
            lines.add("The vehicle history shows an outstanding lien, which must be cleared at sale.");
        }
        if (facts.defects().isEmpty()) {
            lines.add(0, "The seller has not recorded any defects.");
        }
        return String.join(" ", lines);
    }

    private static String defectLine(Defect defect) {
        String type = defect.type().name().toLowerCase(Locale.ROOT);
        String where = defect.bodyLocation().isEmpty() ? "" : " on the " + defect.bodyLocation();
        String note = defect.note().isEmpty() ? "" : " (" + defect.note() + ")";
        return lower(defect.severity()).substring(0, 1).toUpperCase(Locale.ROOT) + lower(defect.severity()).substring(1)
                + " " + type + where + note + ".";
    }

    private static String maintenanceLine(MaintenanceRecord record) {
        return record.date() + ": " + record.description() + " at "
                + String.format(Locale.ROOT, "%,d", record.odometerKm()) + " km";
    }

    private static String bodyStyle(VehicleFacts facts) {
        return facts.bodyStyle() == null ? "vehicle" : facts.bodyStyle().name().toLowerCase(Locale.ROOT);
    }

    private static String lower(Enum<?> value) {
        return value == null ? "" : value.name().toLowerCase(Locale.ROOT);
    }
}
