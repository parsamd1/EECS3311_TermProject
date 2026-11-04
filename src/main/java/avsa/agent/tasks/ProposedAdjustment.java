package avsa.agent.tasks;

/** A qualitative price adjustment the AI suggests, as a percentage of the baseline (negative lowers the price). */
public record ProposedAdjustment(String label, Double percent, String reason) {
}
