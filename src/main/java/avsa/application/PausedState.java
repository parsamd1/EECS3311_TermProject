package avsa.application;

import avsa.domain.PlanStatus;
import avsa.domain.PlanStep;
import avsa.domain.StepStatus;
import java.util.Optional;

/** Stopped after a failed step or a rejected checkpoint; resume retries the step or asks again. */
final class PausedState implements PlanRunState {

    static final PausedState INSTANCE = new PausedState();

    @Override
    public PlanStatus status() {
        return PlanStatus.PAUSED;
    }

    @Override
    public void resume(PlanExecutor ctx) {
        Optional<PlanStep> failed = ctx.current().steps().stream().filter(s -> s.status() == StepStatus.FAILED).findFirst();
        if (failed.isPresent()) {
            failed.get().reset();
            ctx.current().setMessage("");
            ctx.changeState(RunningState.INSTANCE);
        } else if (ctx.current().awaitingStep().isPresent()) {
            ctx.awaitApproval(ctx.current().awaitingStep().get());
        } else {
            ctx.changeState(RunningState.INSTANCE);
        }
    }
}
