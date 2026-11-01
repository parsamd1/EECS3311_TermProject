package avsa.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A written listing (F07). It always carries the result of its last validation, and
 * {@link #isExportable()} is the single rule export checks. Instances are immutable.
 */
public final class GeneratedListing {

    private final String title;
    private final String description;
    private final List<String> highlights;
    private final List<String> maintenanceDetails;
    private final String conditionDisclosure;
    private final Money price;
    private final List<String> seoKeywords;
    private final Tone tone;
    private final boolean templateFallback;
    private final boolean validationOverridden;
    private final boolean editedByUser;
    private final ValidationReport report;

    public GeneratedListing(String title, String description, List<String> highlights, List<String> maintenanceDetails,
                            String conditionDisclosure, Money price, List<String> seoKeywords, Tone tone,
                            boolean templateFallback, boolean validationOverridden, boolean editedByUser,
                            ValidationReport report) {
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.highlights = List.copyOf(highlights);
        this.maintenanceDetails = List.copyOf(maintenanceDetails);
        this.conditionDisclosure = Objects.requireNonNull(conditionDisclosure, "conditionDisclosure");
        this.price = Objects.requireNonNull(price, "price");
        this.seoKeywords = List.copyOf(seoKeywords);
        this.tone = Objects.requireNonNull(tone, "tone");
        this.templateFallback = templateFallback;
        this.validationOverridden = validationOverridden;
        this.editedByUser = editedByUser;
        this.report = report;
    }

    public String title() { return title; }
    public String description() { return description; }
    public List<String> highlights() { return highlights; }
    public List<String> maintenanceDetails() { return maintenanceDetails; }
    public String conditionDisclosure() { return conditionDisclosure; }
    public Money price() { return price; }
    public List<String> seoKeywords() { return seoKeywords; }
    public Tone tone() { return tone; }
    public boolean templateFallback() { return templateFallback; }
    public boolean validationOverridden() { return validationOverridden; }
    public boolean editedByUser() { return editedByUser; }
    /** Empty until the listing has been validated. */
    public Optional<ValidationReport> report() { return Optional.ofNullable(report); }

    /** True only when the last validation passed or the seller explicitly overrode it. */
    public boolean isExportable() {
        return validationOverridden || (report != null && report.isValid());
    }

    public GeneratedListing withReport(ValidationReport newReport) {
        return new GeneratedListing(title, description, highlights, maintenanceDetails, conditionDisclosure, price,
                seoKeywords, tone, templateFallback, validationOverridden, editedByUser, newReport);
    }

    public GeneratedListing withOverride() {
        return new GeneratedListing(title, description, highlights, maintenanceDetails, conditionDisclosure, price,
                seoKeywords, tone, templateFallback, true, editedByUser, report);
    }

    /** The seller's edit. It clears the last validation and any override, so it must be validated again. */
    public GeneratedListing withEdit(String newTitle, String newDescription, List<String> newHighlights,
                                     List<String> newMaintenanceDetails, String newDisclosure) {
        return new GeneratedListing(newTitle, newDescription, newHighlights, newMaintenanceDetails, newDisclosure, price,
                seoKeywords, tone, templateFallback, false, true, null);
    }
}
