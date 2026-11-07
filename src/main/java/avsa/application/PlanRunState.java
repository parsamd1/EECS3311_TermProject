package avsa.application;

import avsa.domain.CheckpointDecision;
import avsa.domain.PlanStatus;

/**
 * State: what a guided-workflow run does when it is advanced, approved, resumed or cancelled depends on
 * the state it is in (F12, Figure 4.1). Actions that make no sense in a state are ignored and explained
 * in the plan's message.
 */
interface PlanRunState {

    PlanStatus status();

    default void advance(PlanExecutor ctx) {
        // Only a running plan moves on by itself.
    }

    default void approve(PlanExecutor ctx, CheckpointDecision decision) {
        ctx.ignored("There is no checkpoint waiting for an answer.");
    }

    default void resume(PlanExecutor ctx) {
        ctx.ignored("Only a paused plan can be resumed.");
    }

    default void cancel(PlanExecutor ctx) {
        ctx.stop();
    }

    /** The state object for a saved plan's status, so a plan can continue in a new process (the CLI). */
    static PlanRunState of(PlanStatus status) {
        return switch (status) {
            case RUNNING -> RunningState.INSTANCE;
            case AWAITING_APPROVAL -> AwaitingApprovalState.INSTANCE;
            case PAUSED -> PausedState.INSTANCE;
            case COMPLETED -> CompletedState.INSTANCE;
            case CANCELLED -> CancelledState.INSTANCE;
        };
    }
}
