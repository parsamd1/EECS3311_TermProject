package avsa.application;

import avsa.domain.PlanStep;
import java.time.Instant;
import java.util.UUID;

/** A guided-workflow step started or finished (F12); the plan view updates from it. */
public final class StepProgress extends AgentEvent {

    private final PlanStep step;

    public StepProgress(Instant occurredAt, UUID sessionId, PlanStep step) {
        super(occurredAt, sessionId);
        this.step = step;
    }

    public PlanStep step() {
        return step;
    }
}
