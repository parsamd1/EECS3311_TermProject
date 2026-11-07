package avsa.agent;

import avsa.agent.tasks.PlanProposal;
import avsa.agent.tasks.PlanProposalTask;
import avsa.domain.Plan;
import avsa.domain.PlanStep;
import avsa.domain.Platform;
import avsa.domain.StepType;
import avsa.domain.StrategyType;
import avsa.domain.VehicleProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns the seller's goal into a validated plan (F12). The AI proposes the steps; when it cannot, a
 * template plan is used and labelled. The validator then fixes prerequisites, order and checkpoints.
 */
public final class Planner {

    /** What the assistant can do, for goals outside its scope. */
    public static final String CAPABILITIES = "I can find comparables, price the vehicle for a quick sale or the "
            + "best price, forecast its value, compare it with the competition, write the listing and format it "
            + "for AutoTrader, Kijiji or Facebook Marketplace.";

    private final AiTaskRunner ai;
    private final PlanValidator validator;
    private final StepCatalog catalog;
    private List<String> lastNotes = List.of();

    public Planner(AiTaskRunner ai, PlanValidator validator, StepCatalog catalog) {
        this.ai = ai;
        this.validator = validator;
        this.catalog = catalog;
    }

    /**
     * @throws IllegalArgumentException the goal is empty, unclear (the message is the AI's clarifying question),
     *                                  or not about selling the vehicle
     */
    public Plan propose(String goal, VehicleProfile profile, boolean autoApprove) {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("Type a goal, for example \"Price my car for a quick sale and write a "
                    + "Kijiji ad\".");
        }
        String vehicle = profile.year() + " " + profile.make() + " " + profile.model()
                + (profile.trim().isBlank() ? "" : " " + profile.trim())
                + String.format(Locale.ROOT, ", %,d km", profile.odometerKm());
        AiResult<PlanProposal> result = ai.run(new PlanProposalTask(goal.strip(), vehicle, catalog));
        lastNotes = result.notes();
        PlanProposal p = result.value();
        if (p.asksQuestion()) {
            throw new IllegalArgumentException("The assistant needs one detail: " + p.question().strip());
        }
        if (p.outsideScope()) {
            throw new IllegalArgumentException(p.outOfScope().strip() + " " + CAPABILITIES);
        }
        List<String> notes = new ArrayList<>();
        if (result.usedFallback()) {
            notes.add("The AI was unavailable, so a template plan was used.");
        }
        List<PlanStep> steps = new ArrayList<>();
        for (String name : p.steps()) {
            Optional<StepType> type = catalog.lookup(name);
            type.ifPresentOrElse(t -> steps.add(new PlanStep(t, false)),
                    () -> notes.add("Ignored an unknown step: " + name + "."));
        }
        StrategyType strategy = p.strategy() == null ? null : StrategyType.valueOf(p.strategy());
        boolean prices = steps.stream().anyMatch(s -> s.type() != StepType.FIND_COMPARABLES);
        if (strategy == null && prices) {
            notes.add("The goal did not say how fast to sell, so maximum profit is assumed; you can change it at "
                    + "the price checkpoint.");
        }
        List<Platform> platforms = p.platforms().stream().map(Platform::valueOf).distinct().toList();
        Plan draft = new Plan(UUID.randomUUID(), goal.strip(), Plan.DEFAULT_MAX_STEPS, autoApprove,
                result.usedFallback(), strategy, p.live(), platforms, steps, notes);
        return validator.validate(draft);
    }

    /** What the user should know about the last proposal, such as an AI step that used its fallback. */
    public List<String> lastNotes() {
        return lastNotes;
    }
}
