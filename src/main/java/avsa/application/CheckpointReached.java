package avsa.application;

import avsa.domain.PlanStep;
import java.time.Instant;
import java.util.UUID;

/** A guided workflow is waiting for the seller to approve a step's result (F12). */
public final class CheckpointReached extends AgentEvent {

    private final PlanStep step;

    public CheckpointReached(Instant occurredAt, UUID sessionId, PlanStep step) {
        super(occurredAt, sessionId);
        this.step = step;
    }

    public PlanStep step() {
        return step;
    }
}
