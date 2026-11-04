package avsa.agent.tasks;

import java.util.List;

/** The AI's explanation of a price, with up to three proposed adjustments. */
public record PriceExplanation(String explanation, List<ProposedAdjustment> adjustments) {
}
