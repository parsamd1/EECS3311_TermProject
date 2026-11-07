package avsa.agent.tasks;

import java.util.List;

/**
 * The AI's proposed plan for a seller's goal (F12): step names from the catalog, the strategy and platforms
 * the goal implies, and either a clarifying question or an out-of-scope note instead of steps.
 */
public record PlanProposal(List<String> steps, String strategy, List<String> platforms, Boolean liveSearch,
                           String question, String outOfScope) {

    public PlanProposal {
        steps = steps == null ? List.of() : List.copyOf(steps);
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
    }

    public boolean live() {
        return Boolean.TRUE.equals(liveSearch);
    }

    public boolean asksQuestion() {
        return question != null && !question.isBlank();
    }

    public boolean outsideScope() {
        return outOfScope != null && !outOfScope.isBlank();
    }
}
