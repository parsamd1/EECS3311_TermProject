package avsa.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A guided workflow (F12): the seller's goal, the ordered steps that reach it, and the choices
 * the steps need (strategy, platforms, data source). The steps were proposed by the AI, or taken
 * from a template when it was unavailable, and checked by the plan validator.
 */
public final class Plan {

    public static final int DEFAULT_MAX_STEPS = 8;

    private final UUID id;
    private final String goal;
    private final int maxSteps;
    private final boolean autoApprove;
    private final boolean templatePlan;
    private StrategyType strategy;
    private final boolean liveSearch;
    private final List<Platform> platforms;
    private final List<PlanStep> steps;
    private final List<String> notes;
    private PlanStatus status = PlanStatus.RUNNING;
    private StepType awaiting;
    private String message = "";
    private SalesPackage salesPackage;
    private PriceRecommendation proposedPrice;

    public Plan(UUID id, String goal, int maxSteps, boolean autoApprove, boolean templatePlan, StrategyType strategy,
                boolean liveSearch, List<Platform> platforms, List<PlanStep> steps, List<String> notes) {
        this.id = Objects.requireNonNull(id);
        this.goal = Objects.requireNonNull(goal);
        this.maxSteps = maxSteps;
        this.autoApprove = autoApprove;
        this.templatePlan = templatePlan;
        this.strategy = strategy == null ? StrategyType.MAX_PROFIT : strategy;
        this.liveSearch = liveSearch;
        this.platforms = platforms == null ? List.of() : List.copyOf(platforms);
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        this.notes = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
    }

    public UUID id() { return id; }
    public String goal() { return goal; }
    public int maxSteps() { return maxSteps; }
    public boolean autoApprove() { return autoApprove; }
    public boolean templatePlan() { return templatePlan; }
    public StrategyType strategy() { return strategy; }
    public boolean liveSearch() { return liveSearch; }
    public List<Platform> platforms() { return platforms; }
    public List<PlanStep> steps() { return List.copyOf(steps); }
    public List<String> notes() { return List.copyOf(notes); }
    public PlanStatus status() { return status; }
    public String message() { return message; }
    public Optional<SalesPackage> salesPackage() { return Optional.ofNullable(salesPackage); }

    /** The price the pricing step computed, accepted only when the seller approves it. */
    public Optional<PriceRecommendation> proposedPrice() { return Optional.ofNullable(proposedPrice); }
    public void setProposedPrice(PriceRecommendation rec) { this.proposedPrice = rec; }

    public Optional<PlanStep> nextPendingStep() {
        return steps.stream().filter(s -> s.status() == StepStatus.PENDING).findFirst();
    }

    public Optional<PlanStep> step(StepType type) {
        return steps.stream().filter(s -> s.type() == type).findFirst();
    }

    /** The checkpoint step whose result is waiting for the seller's approval. */
    public Optional<PlanStep> awaitingStep() {
        return awaiting == null ? Optional.empty() : step(awaiting);
    }

    public boolean isFinished() {
        return status == PlanStatus.COMPLETED || status == PlanStatus.CANCELLED;
    }

    public void setStatus(PlanStatus newStatus) { this.status = Objects.requireNonNull(newStatus); }
    public void setAwaiting(StepType type) { this.awaiting = type; }
    public void setMessage(String text) { this.message = text == null ? "" : text; }
    public void setSalesPackage(SalesPackage result) { this.salesPackage = result; }
    public void addNote(String note) { notes.add(note); }

    /** Maximum profit becomes quick sale and the other way round. */
    public void switchStrategy() {
        strategy = strategy == StrategyType.MAX_PROFIT ? StrategyType.QUICK_SALE : StrategyType.MAX_PROFIT;
    }

    /** Marks a step and every later step to run again. */
    public void rerunFrom(StepType type) {
        boolean reached = false;
        for (PlanStep s : steps) {
            reached |= s.type() == type;
            if (reached) {
                s.reset();
            }
        }
    }

    /** Steps that had not run when the plan was cancelled. */
    public void skipRemaining() {
        steps.stream().filter(s -> s.status() == StepStatus.PENDING || s.status() == StepStatus.RUNNING)
                .forEach(s -> s.finish(StepStatus.SKIPPED, "Not run: the plan was cancelled."));
    }
}
