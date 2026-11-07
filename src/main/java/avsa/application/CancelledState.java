package avsa.application;

import avsa.domain.CheckpointDecision;
import avsa.domain.PlanStatus;

/** Stopped by the seller: finished steps keep their results, the rest are skipped, nothing is exported. */
final class CancelledState implements PlanRunState {

    static final CancelledState INSTANCE = new CancelledState();

    @Override
    public PlanStatus status() {
        return PlanStatus.CANCELLED;
    }

    @Override
    public void approve(PlanExecutor ctx, CheckpointDecision decision) {
        ctx.ignored("The plan was cancelled. Start a new one.");
    }

    @Override
    public void resume(PlanExecutor ctx) {
        ctx.ignored("The plan was cancelled. Start a new one.");
    }

    @Override
    public void cancel(PlanExecutor ctx) {
        ctx.ignored("The plan is already cancelled.");
    }
}
