package avsa.application;

import avsa.domain.PlanStatus;
import avsa.domain.PlanStep;

/** Runs the next step; moves to awaiting approval at a checkpoint, paused on a failure, completed at the end. */
final class RunningState implements PlanRunState {

    static final RunningState INSTANCE = new RunningState();

    @Override
    public PlanStatus status() {
        return PlanStatus.RUNNING;
    }

    @Override
    public void advance(PlanExecutor ctx) {
        if (ctx.current().nextPendingStep().isEmpty()) {
            ctx.complete();
            return;
        }
        StepResult result = ctx.runNextStep();
        PlanStep step = result.step();
        if (!result.ok()) {
            ctx.current().setMessage("Step \"" + PlanExecutor.label(step.type()) + "\" failed: " + result.summary()
                    + " Fix the cause, then resume, or cancel the plan.");
            ctx.changeState(PausedState.INSTANCE);
        } else if (step.checkpoint() && !ctx.current().autoApprove()) {
            ctx.awaitApproval(step);
        } else {
            if (step.checkpoint()) {
                ctx.applyApproval(step);
            }
            if (ctx.current().nextPendingStep().isEmpty()) {
                ctx.complete();
            }
        }
    }

    @Override
    public void resume(PlanExecutor ctx) {
        ctx.ignored("The plan is already running.");
    }
}
