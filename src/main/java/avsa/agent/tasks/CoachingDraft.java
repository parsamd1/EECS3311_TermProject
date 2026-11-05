package avsa.agent.tasks;

import java.util.List;

/** The AI's coaching after a simulated negotiation. */
public record CoachingDraft(String disclosureFeedback, List<String> redFlags) {
}
