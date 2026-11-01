package avsa.domain;

import java.util.List;
import java.util.Objects;

/** The seller's choices for a listing: tone, optional sections and extra keywords. */
public record ListingOptions(Tone tone, boolean includeFeatures, boolean includeMaintenance, List<String> keywords) {

    public ListingOptions {
        Objects.requireNonNull(tone, "tone");
        keywords = List.copyOf(keywords);
    }

    public static ListingOptions defaults() {
        return new ListingOptions(Tone.PROFESSIONAL, true, true, List.of());
    }
}
