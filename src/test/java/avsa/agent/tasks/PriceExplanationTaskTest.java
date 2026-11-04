package avsa.agent.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.AdjustmentKind;
import avsa.domain.Confidence;
import avsa.domain.Money;
import avsa.domain.PriceAdjustment;
import avsa.domain.PriceRange;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.StrategyType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceExplanationTaskTest {

    private static final PriceRecommendation REC = new PriceRecommendation(StrategyType.MAX_PROFIT, Money.cad(29_000),
            new PriceRange(Money.cad(26_600), Money.cad(28_000)), new PriceRange(Money.cad(22_900), Money.cad(24_100)),
            Money.cad(29_500), Money.cad(24_100), Confidence.HIGH,
            List.of(PriceAdjustment.computed("minor scratch", Money.cad(-290), AdjustmentKind.CONDITION)), List.of(),
            List.of(), PricingParameters.defaults(), Instant.parse("2026-11-04T12:00:00Z"));

    private final PriceExplanationTask task = new PriceExplanationTask(REC, null, 8);

    private static PriceExplanation explain(String text, ProposedAdjustment... adjustments) {
        return new PriceExplanation(text, List.of(adjustments));
    }

    @Test
    void anExplanationUsingTheComputedFiguresPasses() {
        assertTrue(task.validate(explain("Ask $29,500; private buyers pay $26,600 to $28,000. The scratch costs "
                + "about $300. Never take less than $24,100.", new ProposedAdjustment("Winter tires included", 1.0,
                "A second set of tires has value."))).isEmpty());
    }

    @Test
    void anInventedNumberIsRejected() {
        List<String> problems = task.validate(explain("This car is worth $33,000."));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("$33,000"));
    }

    @Test
    void proposalsAreCappedLimitedAndCannotRepeatComputedAdjustments() {
        assertFalse(task.validate(explain("Fine.", new ProposedAdjustment("Remote starter", 5.0, ""))).isEmpty());
        assertFalse(task.validate(explain("Fine.", new ProposedAdjustment("Low mileage", 2.0, ""))).isEmpty());
        assertFalse(task.validate(explain("Fine.", new ProposedAdjustment("Scratch repair", -1.0, ""))).isEmpty());
        ProposedAdjustment ok = new ProposedAdjustment("Winter tires", 1.0, "");
        assertFalse(task.validate(explain("Fine.", ok, ok, ok, ok)).isEmpty(), "at most three");
        assertTrue(task.validate(explain("Fine.", ok, ok, ok)).isEmpty());
    }

    @Test
    void proposalsBecomeUnacceptedAdjustmentsOfTheBaseline() {
        List<PriceAdjustment> p = PriceExplanationTask.proposals(explain("Fine.",
                new ProposedAdjustment("Winter tires included", 1.0, "Worth something")), REC.baseline());
        assertEquals(Money.cad(300), p.get(0).amount(), "1% of $29,000 rounded to $50");
        assertEquals(AdjustmentKind.AI_PROPOSED, p.get(0).kind());
        assertFalse(p.get(0).accepted());
        assertEquals("Winter tires included: Worth something", p.get(0).label());
    }

    @Test
    void theFallbackExplainsWithATemplateAndProposesNothing() {
        PriceExplanation fallback = task.fallback();
        assertTrue(fallback.explanation().contains("$29500"));
        assertTrue(fallback.adjustments().isEmpty());
    }
}
