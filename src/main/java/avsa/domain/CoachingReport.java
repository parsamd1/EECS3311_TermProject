package avsa.domain;

import java.util.List;
import java.util.Objects;

/**
 * Feedback after a simulated negotiation (F09). The floor feedback comes from the negotiation
 * tracker, not the AI.
 */
public record CoachingReport(String disclosureFeedback, String floorFeedback, List<String> redFlags,
                             boolean aiWritten) {

    public CoachingReport {
        Objects.requireNonNull(disclosureFeedback, "disclosureFeedback");
        Objects.requireNonNull(floorFeedback, "floorFeedback");
        redFlags = List.copyOf(redFlags);
    }
}
