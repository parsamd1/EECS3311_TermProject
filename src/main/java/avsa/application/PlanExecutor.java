package avsa.application;

import avsa.domain.CheckpointDecision;
import avsa.domain.Plan;
import avsa.domain.PlanStep;
import avsa.domain.SalesPackage;
import avsa.domain.StepStatus;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * State context for a guided workflow (F12). It holds the plan and its current {@link PlanRunState}, and
 * delegates every action to that state; the states call back to run steps and change state. Running
 * continues until the plan waits for approval, pauses, completes or is cancelled, publishing an event for
 * every step so the views and autosave follow along.
 */
public final class PlanExecutor {

    static final int MAX_RETRIES = 2;

    private final StepRunner runner;
    private final EventBus events;
    private final Clock clock;
    private Plan plan;
    private UUID sessionId;
    private volatile PlanRunState state;
    private volatile boolean running;
    private volatile boolean cancelRequested;
    private String notice = "";

    PlanExecutor(StepRunner runner, EventBus events, Clock clock) {
        this.runner = runner;
        this.events = events;
        this.clock = clock;
    }

    /** Starts a new plan and runs it as far as it can go. */
    public void execute(Plan newPlan, UUID session) {
        plan = newPlan;
        sessionId = session;
        cancelRequested = false;
        notice = "";
        changeState(RunningState.INSTANCE);
        loop();
    }

    /** Continues a saved plan, for example in a new CLI process. */
    void restore(Plan saved, UUID session) {
        plan = saved;
        sessionId = session;
        state = PlanRunState.of(saved.status());
    }

    public void approve(CheckpointDecision decision) {
        notice = "";
        state().approve(this, decision);
        loop();
    }

    public void resume() {
        notice = "";
        state().resume(this);
        loop();
    }

    /**
     * Cancels the plan. While a step is running (the GUI calls this from another thread) the plan stops after
     * that step; otherwise it stops at once.
     */
    public void cancel() {
        notice = "";
        if (running) {
            cancelRequested = true;
        } else {
            state().cancel(this);
        }
    }

    public Optional<Plan> plan() {
        return Optional.ofNullable(plan);
    }

    /** Why the last action did nothing, such as "resume" while waiting for approval; empty otherwise. */
    public String notice() {
        return notice;
    }

    Plan current() {
        return plan;
    }

    private PlanRunState state() {
        if (state == null) {
            throw new IllegalStateException("No guided plan yet. Start one with a goal.");
        }
        return state;
    }

    private void loop() {
        running = true;
        try {
            while (state == RunningState.INSTANCE) {
                if (cancelRequested) {
                    cancelRequested = false;
                    state.cancel(this);
                } else {
                    state.advance(this);
                }
            }
        } finally {
            running = false;
        }
    }

    void changeState(PlanRunState next) {
        state = next;
        plan.setStatus(next.status());
        events.publish(new SessionUpdated(clock.instant(), sessionId, "plan"));
    }

    /** Runs the next pending step, retrying it up to {@link #MAX_RETRIES} times. */
    StepResult runNextStep() {
        PlanStep step = plan.nextPendingStep().orElseThrow();
        String problem = "";
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            step.start();
            events.publish(new StepProgress(clock.instant(), sessionId, step));
            try {
                String summary = runner.run(plan, step);
                step.finish(StepStatus.DONE, summary);
                events.publish(new StepProgress(clock.instant(), sessionId, step));
                return new StepResult(step, true, summary);
            } catch (RuntimeException e) {
                problem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }
        step.finish(StepStatus.FAILED, problem);
        events.publish(new StepProgress(clock.instant(), sessionId, step));
        return new StepResult(step, false, problem);
    }

    void awaitApproval(PlanStep step) {
        plan.setAwaiting(step.type());
        plan.setMessage("Waiting for your approval: " + label(step.type()) + ". " + step.resultSummary());
        changeState(AwaitingApprovalState.INSTANCE);
        events.publish(new CheckpointReached(clock.instant(), sessionId, step));
    }

    void applyApproval(PlanStep step) {
        runner.approved(plan, step);
    }

    void complete() {
        SalesPackage result = runner.finish(plan);
        plan.setSalesPackage(result);
        plan.setMessage(result.summary());
        changeState(CompletedState.INSTANCE);
    }

    void stop() {
        plan.skipRemaining();
        plan.setAwaiting(null);
        plan.setMessage("Cancelled. Finished steps keep their results; nothing was exported.");
        changeState(CancelledState.INSTANCE);
    }

    void ignored(String why) {
        notice = why;
    }

    static String label(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
