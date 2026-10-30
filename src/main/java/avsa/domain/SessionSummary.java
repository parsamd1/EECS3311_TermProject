package avsa.domain;

import java.time.Instant;
import java.util.UUID;

/** One line of the saved-sessions list. */
public record SessionSummary(UUID id, String name, Instant lastModified) {
}
