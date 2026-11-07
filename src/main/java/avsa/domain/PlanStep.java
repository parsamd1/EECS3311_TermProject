package avsa.domain;

import java.util.Objects;

/** One step of a guided workflow, with its progress (F12). */
public final class PlanStep {

    private final StepType type;
    private final boolean checkpoint;
    private StepStatus status = StepStatus.PENDING;
    private int attempts;
    private String resultSummary = "";

    public PlanStep(StepType type, boolean checkpoint) {
        this.type = Objects.requireNonNull(type);
        this.checkpoint = checkpoint;
    }

    public StepType type() { return type; }
    public boolean checkpoint() { return checkpoint; }
    public StepStatus status() { return status; }
    public int attempts() { return attempts; }
    public String resultSummary() { return resultSummary; }

    public void start() {
        status = StepStatus.RUNNING;
        attempts++;
    }

    public void finish(StepStatus result, String summary) {
        status = Objects.requireNonNull(result);
        resultSummary = summary == null ? "" : summary;
    }

    /** Runs the step again, for example after the seller changed the strategy. */
    public void reset() {
        status = StepStatus.PENDING;
        attempts = 0;
        resultSummary = "";
    }
}
