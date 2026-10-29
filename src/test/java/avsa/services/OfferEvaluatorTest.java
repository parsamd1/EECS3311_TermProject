package avsa.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.BuyerType;
import avsa.domain.Confidence;
import avsa.domain.DepreciationForecast;
import avsa.domain.Money;
import avsa.domain.Offer;
import avsa.domain.OfferEvaluation;
import avsa.domain.OfferVerdict;
import avsa.domain.PriceRange;
import avsa.domain.PriceRecommendation;
import avsa.domain.PricingParameters;
import avsa.domain.Segment;
import avsa.domain.StrategyType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfferEvaluatorTest {

    private final OfferEvaluator evaluator = new OfferEvaluator();

    /**
     * The author's car: retail ~$28-29k; with the 14% dealer spread the wholesale range is
     * $24,100-24,900 (midpoint $24,500). Maximum-profit floor = top of wholesale.
     */
    private static PriceRecommendation authorsCar() {
        return new PriceRecommendation(StrategyType.MAX_PROFIT, Money.cad(30_000),
                new PriceRange(Money.cad(28_000), Money.cad(29_000)),
                new PriceRange(Money.cad(24_100), Money.cad(24_900)),
                Money.cad(30_500), Money.cad(24_900), Confidence.MEDIUM, List.of(), List.of(), List.of(),
                PricingParameters.defaults(), Instant.parse("2026-10-29T12:00:00Z"));
    }

    private OfferEvaluation evaluate(long amount, DepreciationForecast forecast) {
        Offer offer = new Offer(Money.cad(amount), BuyerType.DEALER, "", Instant.parse("2026-10-29T13:00:00Z"));
        return evaluator.evaluate(offer, authorsCar(), forecast);
    }

    private OfferEvaluation evaluate(long amount) {
        return evaluate(amount, null);
    }

    @Test
    void authorsRealDealerOffersSpanLowBallToStrong() {
        OfferEvaluation lowest = evaluate(23_500);
        assertEquals(OfferVerdict.LOW_BALL, lowest.verdict());
        assertEquals(Money.cad(24_900), lowest.counterAmount().orElseThrow(), "counter at top of wholesale");

        OfferEvaluation highest = evaluate(25_500);
        assertEquals(OfferVerdict.STRONG, highest.verdict());
        assertTrue(highest.recommendsAccepting());
        assertTrue(highest.counterAmount().isEmpty());
        assertEquals(Money.cad(-600), highest.gapToWholesaleTop());
    }

    @Test
    void classificationBoundaries() {
        assertEquals(OfferVerdict.LOW_BALL, evaluate(24_099).verdict());
        assertEquals(OfferVerdict.BELOW_AVERAGE, evaluate(24_100).verdict(), "bottom of range is inside it");
        assertEquals(OfferVerdict.BELOW_AVERAGE, evaluate(24_499).verdict());
        assertEquals(OfferVerdict.FAIR, evaluate(24_500).verdict(), "midpoint starts the upper half");
        assertEquals(OfferVerdict.FAIR, evaluate(24_900).verdict(), "top of range is inside it");
        assertEquals(OfferVerdict.STRONG, evaluate(24_901).verdict());
    }

    @Test
    void belowAverageIsCounteredAtTopOfWholesale() {
        assertEquals(Money.cad(24_900), evaluate(24_200).counterAmount().orElseThrow());
    }

    @Test
    void fairOfferIsCounteredHalfwayToTheTop() {
        OfferEvaluation fair = evaluate(24_600);
        assertEquals(Money.cad(24_800), fair.counterAmount().orElseThrow(), "(24,600 + 24,900) / 2, rounded");
        assertEquals(0.625, fair.positionInWholesale(), 1e-9);
        assertEquals(Money.cad(300), fair.gapToWholesaleTop());
    }

    @Test
    void flagsOffersBelowTheWalkAwayFloor() {
        assertTrue(evaluate(24_600).belowWalkAwayFloor());
        assertFalse(evaluate(25_000).belowWalkAwayFloor());
    }

    @Test
    void offerAboveRetailMustBeConfirmed() {
        OfferEvaluation e = evaluate(29_500);
        assertTrue(e.aboveRetailRange());
        assertTrue(e.warnings().get(0).contains("confirm the amount"));
        assertFalse(evaluate(28_500).aboveRetailRange());
    }

    @Test
    void stalePriceProducesWarning() {
        PriceRecommendation rec = authorsCar();
        rec.markStale();
        Offer offer = new Offer(Money.cad(24_600), BuyerType.DEALER, null, Instant.now());
        assertTrue(evaluator.evaluate(offer, rec, null).warnings().get(0).contains("out of date"));
    }

    @Test
    void comparesWithWaitingWhenAForecastExists() {
        DepreciationForecast forecast = new DepreciationCalculator(DepreciationRateTable.builtIn()).forecast(
                new ForecastInput(Money.cad(28_500), 2022, Segment.SUV, LocalDate.of(2026, 10, 29),
                        Money.cad(150), Money.cad(24_500)));
        OfferEvaluation e = evaluate(24_600, forecast);
        assertEquals(forecast.pointAt(3).orElseThrow().netIfSoldThen(), e.netIfWaiting().orElseThrow());
        assertTrue(evaluate(24_600).netIfWaiting().isEmpty());
    }

    @Test
    void nonPositiveOfferIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> evaluate(0));
        assertThrows(IllegalArgumentException.class, () -> evaluate(-5));
    }

    @Test
    void scriptIsAttachedLater() {
        OfferEvaluation e = evaluate(23_500);
        assertTrue(e.script().isEmpty());
        e.setScript("Thanks for the offer...", true);
        assertTrue(e.templateScript());
    }
}
