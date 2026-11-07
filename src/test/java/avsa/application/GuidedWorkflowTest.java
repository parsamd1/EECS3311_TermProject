package avsa.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.agent.MockLLMClient;
import avsa.domain.BodyStyle;
import avsa.domain.CheckpointDecision;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.FuelType;
import avsa.domain.Location;
import avsa.domain.Plan;
import avsa.domain.PlanStatus;
import avsa.domain.Platform;
import avsa.domain.SalesPackage;
import avsa.domain.Session;
import avsa.domain.Severity;
import avsa.domain.StepStatus;
import avsa.domain.StepType;
import avsa.domain.StrategyType;
import avsa.domain.Transmission;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.AppConfig;
import avsa.infrastructure.JsonMapper;
import avsa.infrastructure.JsonSessionRepository;
import avsa.infrastructure.SchemaMigrator;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** F12 through the facade: the AI plans, every later AI call falls back, and the steps use the real features. */
class GuidedWorkflowTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private static final String QUICK_KIJIJI = "{\"steps\": [\"RECOMMEND_PRICE\", \"FORECAST\", \"ANALYZE_POSITIONING\", "
            + "\"GENERATE_LISTING\", \"EXPORT_LISTING\"], \"strategy\": \"QUICK_SALE\", \"platforms\": [\"KIJIJI\"], "
            + "\"liveSearch\": false, \"question\": null, \"outOfScope\": null}";

    @TempDir
    Path dir;

    private final MockLLMClient llm = new MockLLMClient();

    private AgentController controller() {
        AgentController c = AvsaApplication.create(AppConfig.load(dir.resolve("none.env"), Map.of()), dir, CLOCK, llm);
        c.saveProfile(VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS")
                .bodyStyle(BodyStyle.SUV).odometerKm(49_000).transmission(Transmission.AUTOMATIC)
                .fuelType(FuelType.GASOLINE).location(new Location("Toronto", "ON", 43.6532, -79.3832))
                .condition(new ConditionReport(null, List.of(new Defect(DefectType.SCRATCH, "rear door",
                        Severity.MINOR, "", null)))).build());
        return c;
    }

    @Test
    void aGoalRunsToTheSalesPackageWithApprovalAtThePriceAndTheListing() {
        AgentController c = controller();
        List<String> events = new ArrayList<>();
        c.events().subscribe(AgentEvent.class, e -> events.add(e.getClass().getSimpleName()));
        llm.thenRespond(QUICK_KIJIJI);

        Plan plan = c.runGoal("Price my car for a quick sale and write a Kijiji ad");

        assertEquals(PlanStatus.AWAITING_APPROVAL, plan.status());
        assertEquals(StepType.RECOMMEND_PRICE, plan.awaitingStep().orElseThrow().type());
        assertTrue(plan.awaitingStep().orElseThrow().resultSummary().startsWith("quick sale: asking $"));
        assertTrue(c.sessions().current().activePrice().isEmpty(), "nothing is accepted before approval");
        assertTrue(events.contains("StepProgress") && events.contains("CheckpointReached"));

        plan = c.answerCheckpoint(CheckpointDecision.APPROVE);
        assertEquals(StepType.GENERATE_LISTING, plan.awaitingStep().orElseThrow().type());
        assertEquals(StrategyType.QUICK_SALE, c.sessions().current().activePrice().orElseThrow().strategy());
        assertTrue(c.sessions().current().forecast().isPresent());
        assertTrue(c.sessions().current().positioning().isPresent());

        plan = c.answerCheckpoint(CheckpointDecision.APPROVE);
        assertEquals(PlanStatus.COMPLETED, plan.status());
        SalesPackage result = plan.salesPackage().orElseThrow();
        assertEquals(Platform.KIJIJI, result.exports().get(0).profile().platform());
        assertTrue(result.summary().contains("formatted for Kijiji"), result.summary());
        assertTrue(plan.steps().stream().allMatch(s -> s.status() == StepStatus.DONE));

        Session saved = new JsonSessionRepository(dir.resolve("sessions"), new JsonMapper(), new SchemaMigrator())
                .load(c.sessions().current().id());
        assertEquals(PlanStatus.COMPLETED, saved.plan().orElseThrow().status());
        assertTrue(saved.plan().orElseThrow().salesPackage().isPresent());
    }

    @Test
    void theSellerCanChangeTheStrategyRejectResumeAndCancel() {
        AgentController c = controller();
        llm.thenRespond("{\"steps\": [\"FORECAST\"], \"strategy\": null, \"platforms\": []}");
        Plan plan = c.runGoal("How much does it lose if I wait?");
        assertEquals(StrategyType.MAX_PROFIT, plan.strategy());
        assertTrue(plan.notes().stream().anyMatch(n -> n.contains("maximum profit is assumed")));

        plan = c.answerCheckpoint(CheckpointDecision.CHANGE_STRATEGY);
        assertEquals(PlanStatus.AWAITING_APPROVAL, plan.status());
        assertTrue(plan.awaitingStep().orElseThrow().resultSummary().startsWith("quick sale"));

        plan = c.answerCheckpoint(CheckpointDecision.REJECT);
        assertEquals(PlanStatus.PAUSED, plan.status());
        plan = c.resumeRun();
        assertEquals(PlanStatus.AWAITING_APPROVAL, plan.status());
        c.resumeRun();
        assertEquals(List.of("The plan is waiting for your answer at the checkpoint."), c.lastNotes());

        plan = c.cancelRun();
        assertEquals(PlanStatus.CANCELLED, plan.status());
        assertEquals(StepStatus.SKIPPED, plan.step(StepType.FORECAST).orElseThrow().status());
        assertTrue(c.sessions().current().activePrice().isEmpty(), "a cancelled plan accepts nothing");
    }

    @Test
    void withoutAPlanOrAProfileTheSellerIsToldWhatToDoFirst() {
        AgentController c = controller();
        assertThrows(IllegalStateException.class, c::resumeRun);
        AgentController fresh = AvsaApplication.create(AppConfig.load(dir.resolve("none.env"), Map.of()),
                dir.resolve("other"), CLOCK, llm);
        assertThrows(IllegalStateException.class, () -> fresh.runGoal("Sell my car"));
        assertFalse(fresh.currentPlan().isPresent());
    }
}
