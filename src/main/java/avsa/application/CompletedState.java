package avsa.application;

import avsa.domain.CheckpointDecision;
import avsa.domain.PlanStatus;

/** Finished: the sales package is ready and nothing else changes the plan. */
final class CompletedState implements PlanRunState {

    static final CompletedState INSTANCE = new CompletedState();

    @Override
    public PlanStatus status() {
        return PlanStatus.COMPLETED;
    }

    @Override
    public void approve(PlanExecutor ctx, CheckpointDecision decision) {
        ctx.ignored("The plan is already complete.");
    }

    @Override
    public void resume(PlanExecutor ctx) {
        ctx.ignored("The plan is already complete.");
    }

    @Override
    public void cancel(PlanExecutor ctx) {
        ctx.ignored("The plan is already complete.");
    }
}
