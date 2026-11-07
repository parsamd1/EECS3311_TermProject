package avsa.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.CheckpointDecision;
import avsa.domain.Plan;
import avsa.domain.PlanStatus;
import avsa.domain.PlanStep;
import avsa.domain.SalesPackage;
import avsa.domain.StepStatus;
import avsa.domain.StepType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The State pattern of F12, with a fake step runner so each transition can be checked on its own. */
class PlanExecutorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);

    private final Map<StepType, Integer> failuresLeft = new EnumMap<>(StepType.class);
    private final List<StepType> approved = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private Runnable duringRun = () -> { };
    private final EventBus bus = new EventBus();
    private final PlanExecutor executor = new PlanExecutor(new StepRunner() {
        @Override
        public String run(Plan plan, PlanStep step) {
            duringRun.run();
            if (failuresLeft.merge(step.type(), -1, Integer::sum) >= 0) {
                throw new IllegalStateException("network down");
            }
            return step.type() + " ok";
        }

        @Override
        public void approved(Plan plan, PlanStep step) {
            approved.add(step.type());
        }

        @Override
        public SalesPackage finish(Plan plan) {
            return new SalesPackage("done", null, null, null, null, List.of());
        }
    }, bus, CLOCK);

    PlanExecutorTest() {
        bus.subscribe(AgentEvent.class, e -> events.add(e.getClass().getSimpleName()));
    }

    private static Plan plan(boolean autoApprove) {
        return new Plan(UUID.randomUUID(), "Price it and forecast", Plan.DEFAULT_MAX_STEPS, autoApprove, false, null,
                false, List.of(), List.of(new PlanStep(StepType.FIND_COMPARABLES, false),
                new PlanStep(StepType.RECOMMEND_PRICE, true), new PlanStep(StepType.FORECAST, false)), List.of());
    }

    @Test
    void aCheckpointWaitsForApprovalThenTheRunCompletes() {
        Plan p = plan(false);
        executor.execute(p, UUID.randomUUID());
        assertEquals(PlanStatus.AWAITING_APPROVAL, p.status());
        assertEquals(StepType.RECOMMEND_PRICE, p.awaitingStep().orElseThrow().type());
        assertEquals(StepStatus.PENDING, p.step(StepType.FORECAST).orElseThrow().status());
        assertTrue(events.contains("CheckpointReached"));

        executor.resume();
        assertEquals("The plan is waiting for your answer at the checkpoint.", executor.notice());
        assertEquals(PlanStatus.AWAITING_APPROVAL, p.status());

        executor.approve(CheckpointDecision.APPROVE);
        assertEquals(PlanStatus.COMPLETED, p.status());
        assertEquals(List.of(StepType.RECOMMEND_PRICE), approved);
        assertEquals("done", p.salesPackage().orElseThrow().summary());
        executor.resume();
        assertEquals("The plan is already complete.", executor.notice());
    }

    @Test
    void autoApproveRunsStraightThrough() {
        Plan p = plan(true);
        executor.execute(p, UUID.randomUUID());
        assertEquals(PlanStatus.COMPLETED, p.status());
        assertEquals(List.of(StepType.RECOMMEND_PRICE), approved);
    }

    @Test
    void aStepIsRetriedTwiceThenThePlanPausesAndResumeRetriesIt() {
        failuresLeft.put(StepType.FIND_COMPARABLES, 2);
        Plan recovered = plan(true);
        executor.execute(recovered, UUID.randomUUID());
        assertEquals(PlanStatus.COMPLETED, recovered.status(), "two failures are within the retries");
        assertEquals(3, recovered.step(StepType.FIND_COMPARABLES).orElseThrow().attempts());

        failuresLeft.put(StepType.FIND_COMPARABLES, 3);
        Plan p = plan(true);
        executor.execute(p, UUID.randomUUID());
        assertEquals(PlanStatus.PAUSED, p.status());
        assertEquals(StepStatus.FAILED, p.step(StepType.FIND_COMPARABLES).orElseThrow().status());
        assertTrue(p.message().contains("network down"));

        executor.resume();
        assertEquals(PlanStatus.COMPLETED, p.status());
    }

    @Test
    void rejectingPausesAndResumingAsksAgain() {
        Plan p = plan(false);
        executor.execute(p, UUID.randomUUID());
        executor.approve(CheckpointDecision.REJECT);
        assertEquals(PlanStatus.PAUSED, p.status());
        assertTrue(p.message().startsWith("You rejected"));
        executor.resume();
        assertEquals(PlanStatus.AWAITING_APPROVAL, p.status());
        assertTrue(approved.isEmpty());
    }

    @Test
    void changingTheStrategyRerunsPricingAndTheStepsAfterIt() {
        Plan p = plan(false);
        executor.execute(p, UUID.randomUUID());
        executor.approve(CheckpointDecision.CHANGE_STRATEGY);
        assertEquals(avsa.domain.StrategyType.QUICK_SALE, p.strategy());
        assertEquals(PlanStatus.AWAITING_APPROVAL, p.status(), "the new price needs approval too");
        assertEquals(1, p.step(StepType.RECOMMEND_PRICE).orElseThrow().attempts(), "it ran again from scratch");
    }

    @Test
    void cancellingDuringAStepStopsAfterItAndSkipsTheRest() {
        Plan p = plan(true);
        duringRun = () -> {
            executor.cancel();
            duringRun = () -> { };
        };
        executor.execute(p, UUID.randomUUID());
        assertEquals(PlanStatus.CANCELLED, p.status());
        assertEquals(StepStatus.DONE, p.step(StepType.FIND_COMPARABLES).orElseThrow().status());
        assertEquals(StepStatus.SKIPPED, p.step(StepType.FORECAST).orElseThrow().status());
        executor.approve(CheckpointDecision.APPROVE);
        assertEquals("The plan was cancelled. Start a new one.", executor.notice());
    }

    @Test
    void aSavedPlanContinuesFromItsStatusInANewExecutor() {
        Plan p = plan(false);
        executor.execute(p, UUID.randomUUID());
        PlanExecutor later = new PlanExecutor(new StepRunner() {
            @Override
            public String run(Plan plan, PlanStep step) {
                return "ok";
            }

            @Override
            public void approved(Plan plan, PlanStep step) {
                approved.add(step.type());
            }

            @Override
            public SalesPackage finish(Plan plan) {
                return new SalesPackage("done later", null, null, null, null, List.of());
            }
        }, bus, CLOCK);
        later.restore(p, UUID.randomUUID());
        later.approve(CheckpointDecision.APPROVE);
        assertEquals(PlanStatus.COMPLETED, p.status());
        assertEquals("done later", p.message());
    }
}
