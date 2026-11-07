package avsa.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.BodyStyle;
import avsa.domain.FuelType;
import avsa.domain.Location;
import avsa.domain.Plan;
import avsa.domain.PlanStep;
import avsa.domain.Platform;
import avsa.domain.StepType;
import avsa.domain.StrategyType;
import avsa.domain.Transmission;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlannerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);
    private static final String TEST_VIN = "TESTVIN0000000001";

    private final MockLLMClient llm = new MockLLMClient();
    private final StepCatalog catalog = new StepCatalog();
    private final Planner planner = new Planner(new AiTaskRunner(new PromptComposer(), llm,
            new ResponseParser(new JsonMapper()), new AgentTrace(CLOCK)), new PlanValidator(catalog), catalog);

    private static VehicleProfile cx5() {
        return VehicleProfile.builder().make("Mazda").model("CX-5").year(2022).trim("GS").bodyStyle(BodyStyle.SUV)
                .odometerKm(49_000).transmission(Transmission.AUTOMATIC).fuelType(FuelType.GASOLINE).vin(TEST_VIN)
                .location(new Location("Toronto", "ON", 43.6532, -79.3832)).build();
    }

    private static List<StepType> types(Plan plan) {
        return plan.steps().stream().map(PlanStep::type).toList();
    }

    @Test
    void theAiPlanGetsItsPrerequisitesOrderAndCheckpointsFromTheCode() {
        llm.thenRespond("{\"steps\": [\"EXPORT_LISTING\", \"RECOMMEND_PRICE\"], \"strategy\": \"QUICK_SALE\", "
                + "\"platforms\": [\"KIJIJI\"], \"liveSearch\": false, \"question\": null, \"outOfScope\": null}");

        Plan plan = planner.propose("Price my car for a quick sale and write a Kijiji ad", cx5(), false);

        assertEquals(List.of(StepType.FIND_COMPARABLES, StepType.RECOMMEND_PRICE, StepType.GENERATE_LISTING,
                StepType.EXPORT_LISTING), types(plan));
        assertEquals(List.of(false, true, true, false), plan.steps().stream().map(PlanStep::checkpoint).toList());
        assertEquals(StrategyType.QUICK_SALE, plan.strategy());
        assertEquals(List.of(Platform.KIJIJI), plan.platforms());
        assertFalse(plan.templatePlan());
        assertTrue(plan.notes().stream().anyMatch(n -> n.startsWith("Added find comparables")));
        assertFalse(llm.received().get(0).user().contains(TEST_VIN), "the VIN never reaches the AI");
    }

    @Test
    void anUnknownStepIsSentBackForRepair() {
        llm.thenRespond("{\"steps\": [\"BOOK_A_CAR_WASH\"], \"strategy\": null, \"platforms\": []}")
                .thenRespond("{\"steps\": [\"FORECAST\"], \"strategy\": null, \"platforms\": []}");
        Plan plan = planner.propose("How much will it lose if I wait?", cx5(), false);
        assertEquals(List.of(StepType.FIND_COMPARABLES, StepType.RECOMMEND_PRICE, StepType.FORECAST), types(plan));
        assertTrue(llm.received().get(1).user().contains("Unknown step \"BOOK_A_CAR_WASH\""));
        assertTrue(plan.notes().stream().anyMatch(n -> n.contains("maximum profit is assumed")));
    }

    @Test
    void aMalformedAnswerFallsBackToALabelledTemplatePlanReadFromTheGoal() {
        llm.thenRespond("I would start by looking at the market.").thenRespond("Still not JSON.");
        Plan plan = planner.propose("Sell it fast to a dealer and post it on Kijiji", cx5(), false);
        assertTrue(plan.templatePlan());
        assertEquals(StrategyType.QUICK_SALE, plan.strategy());
        assertEquals(List.of(Platform.KIJIJI), plan.platforms());
        assertEquals(6, plan.steps().size());
        assertTrue(plan.notes().contains("The AI was unavailable, so a template plan was used."));
    }

    @Test
    void anUnclearGoalGetsOneQuestionAndAnUnrelatedOneIsDeclined() {
        llm.thenRespond("{\"steps\": [], \"question\": \"Do you want to sell quickly or for the best price?\"}");
        IllegalArgumentException unclear = assertThrows(IllegalArgumentException.class,
                () -> planner.propose("help with my car", cx5(), false));
        assertTrue(unclear.getMessage().contains("sell quickly or for the best price?"));

        llm.thenRespond("{\"steps\": [], \"outOfScope\": \"Booking flights is not something I do.\"}");
        IllegalArgumentException outside = assertThrows(IllegalArgumentException.class,
                () -> planner.propose("Book me a flight", cx5(), false));
        assertTrue(outside.getMessage().contains(Planner.CAPABILITIES));
        assertThrows(IllegalArgumentException.class, () -> planner.propose("  ", cx5(), false));
    }

    @Test
    void theValidatorDropsAnExportWithNoPlatformAndCapsTheStepCount() {
        PlanValidator validator = new PlanValidator(catalog);
        Plan noPlatform = validator.validate(new Plan(UUID.randomUUID(), "goal", Plan.DEFAULT_MAX_STEPS, false, false,
                null, false, List.of(), List.of(new PlanStep(StepType.EXPORT_LISTING, false)), List.of()));
        assertEquals(List.of(StepType.FIND_COMPARABLES, StepType.RECOMMEND_PRICE, StepType.GENERATE_LISTING),
                types(noPlatform), "the ad is still written, just not formatted for a marketplace");

        Plan capped = validator.validate(new Plan(UUID.randomUUID(), "goal", 2, false, false, null, false,
                List.of(Platform.KIJIJI), List.of(new PlanStep(StepType.EXPORT_LISTING, false)), List.of()));
        assertEquals(2, capped.steps().size());
        assertTrue(capped.notes().contains("The plan was cut to 2 steps."));
    }
}
