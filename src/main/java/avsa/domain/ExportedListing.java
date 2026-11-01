package avsa.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A listing formatted for one marketplace (F08).
 *
 * @param fields          the platform's fields, in display order
 * @param text            the full text the seller copies or saves
 * @param trimmedSections optional sections removed to fit the limits
 * @param withinLimits    false when the text is still too long after trimming
 * @param outputFile      where it was saved; null until it is saved
 */
public record ExportedListing(GeneratedListing listing, PlatformProfile profile, Map<String, String> fields, String text,
                              List<String> warnings, List<String> trimmedSections, boolean withinLimits,
                              Path outputFile) {

    public ExportedListing {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(text, "text");
        warnings = List.copyOf(warnings);
        trimmedSections = List.copyOf(trimmedSections);
    }

    public Optional<Path> savedTo() {
        return Optional.ofNullable(outputFile);
    }
}
