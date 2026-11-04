package avsa.agent.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.BuyerType;
import avsa.domain.ComparableListing;
import avsa.domain.Confidence;
import avsa.domain.Money;
import avsa.domain.Offer;
import avsa.domain.OfferEvaluation;
import avsa.domain.PriceRange;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.StrategyType;
import avsa.services.OfferEvaluator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CounterScriptTaskTest {

    private static final PriceRecommendation PRICE = new PriceRecommendation(StrategyType.MAX_PROFIT, Money.cad(30_000),
            new PriceRange(Money.cad(28_000), Money.cad(29_000)), new PriceRange(Money.cad(24_100), Money.cad(24_900)),
            Money.cad(30_500), Money.cad(24_900), Confidence.MEDIUM, List.of(), List.of(), List.of(),
            PricingParameters.defaults(), Instant.parse("2026-11-04T12:00:00Z"));
    private static final List<ComparableListing> COMPS = List.of(
            new ComparableListing("a", "Kijiji", 2022, "GS", 51_000, Money.cad(28_900), 10, "", ""));

    private static CounterScriptTask task(long amount) {
        OfferEvaluation e = new OfferEvaluator().evaluate(new Offer(Money.cad(amount), BuyerType.DEALER,
                "High kilometres", Instant.parse("2026-11-04T13:00:00Z")), PRICE, null);
        return new CounterScriptTask(e, PRICE, COMPS);
    }

    @Test
    void aScriptThatAsksForTheCounterAndCitesAComparablePasses() {
        assertTrue(task(23_500).validate("Thanks for $23,500. A similar 2022 GS is listed at $28,900, so I can do "
                + "$24,900.").isEmpty());
    }

    @Test
    void theScriptMustAskForTheCounterAndInventNoNumbers() {
        assertEquals(1, task(23_500).validate("Thanks, but I need more.").size());
        List<String> invented = task(23_500).validate("I can do $24,900; a GT sold for $35,000.");
        assertEquals(1, invented.size());
        assertTrue(invented.get(0).contains("$35,000"));
    }

    @Test
    void aStrongOfferNeedsNoCounterAndTheTemplateAcceptsIt() {
        CounterScriptTask strong = task(25_500);
        assertTrue(strong.validate("Thanks for $25,500, that works for me.").isEmpty());
        assertTrue(strong.fallback().contains("That works for me"));
        assertTrue(task(23_500).fallback().contains("I can accept $24900"));
    }
}
