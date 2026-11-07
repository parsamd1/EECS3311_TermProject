package avsa.application;

import avsa.domain.CheckpointDecision;
import avsa.domain.PlanStatus;
import avsa.domain.PlanStep;
import avsa.domain.StepType;

/** Waits for the seller at a checkpoint: approve continues, a strategy change re-runs pricing, reject pauses. */
final class AwaitingApprovalState implements PlanRunState {

    static final AwaitingApprovalState INSTANCE = new AwaitingApprovalState();

    @Override
    public PlanStatus status() {
        return PlanStatus.AWAITING_APPROVAL;
    }

    @Override
    public void approve(PlanExecutor ctx, CheckpointDecision decision) {
        PlanStep step = ctx.current().awaitingStep().orElseThrow();
        switch (decision) {
            case APPROVE -> {
                ctx.applyApproval(step);
                ctx.current().setAwaiting(null);
                ctx.current().setMessage("");
                ctx.changeState(RunningState.INSTANCE);
            }
            case CHANGE_STRATEGY -> {
                ctx.current().switchStrategy();
                ctx.current().rerunFrom(StepType.RECOMMEND_PRICE);
                ctx.current().setAwaiting(null);
                ctx.current().setMessage("Strategy changed to " + PlanExecutor.label(ctx.current().strategy())
                        + "; pricing and the steps after it run again.");
                ctx.changeState(RunningState.INSTANCE);
            }
            case REJECT -> {
                ctx.current().setMessage("You rejected \"" + PlanExecutor.label(step.type()) + "\". Change it in its "
                        + "tab if you like, then resume to be asked again, or cancel the plan.");
                ctx.changeState(PausedState.INSTANCE);
            }
        }
    }

    @Override
    public void resume(PlanExecutor ctx) {
        ctx.ignored("The plan is waiting for your answer at the checkpoint.");
    }
}
