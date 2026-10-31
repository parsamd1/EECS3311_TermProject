package avsa.services;

import avsa.domain.DepreciationForecast;
import avsa.domain.Money;
import avsa.domain.Offer;
import avsa.domain.OfferEvaluation;
import avsa.domain.OfferVerdict;
import avsa.domain.PriceRange;
import avsa.domain.PriceRecommendation;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Judges an offer against the wholesale range with fixed rules (F11).
 */
public final class OfferEvaluator {

    /** Author's market research: typical time to find a private buyer in Canada, and longer in winter. */
    static final double EXPECTED_MONTHS_TO_SELL_PRIVATELY = 1.0;
    static final double EXPECTED_MONTHS_TO_SELL_PRIVATELY_IN_WINTER = 1.5;
    private static final Set<Month> WINTER = Set.of(Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH);

    /**
     * @param forecast may be null; without it no take-now-versus-wait comparison is made
     */
    public OfferEvaluation evaluate(Offer offer, PriceRecommendation rec, DepreciationForecast forecast) {
        Money amount = offer.amount();
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("The offer amount must be greater than zero.");
        }
        PriceRange wholesale = rec.wholesale();
        OfferVerdict verdict = classify(amount, wholesale);
        List<String> warnings = new ArrayList<>();
        boolean aboveRetail = amount.compareTo(rec.retail().high()) > 0;
        if (aboveRetail) {
            warnings.add("The offer is above the retail range. Please confirm the amount was entered correctly.");
        }
        if (rec.stale()) {
            warnings.add("The price recommendation is out of date; re-run pricing for an accurate verdict.");
        }
        Money netIfWaiting = null;
        if (forecast != null) {
            netIfWaiting = forecast.netIfSoldAfter(expectedMonthsToSell(offer.receivedAt()));
        }
        return new OfferEvaluation(offer, verdict, wholesale.positionOf(amount), wholesale.high().minus(amount),
                counter(verdict, amount, wholesale), netIfWaiting,
                amount.compareTo(rec.walkAwayFloor()) < 0, aboveRetail, warnings);
    }

    static double expectedMonthsToSell(Instant when) {
        Month month = when.atZone(ZoneId.systemDefault()).getMonth();
        return WINTER.contains(month) ? EXPECTED_MONTHS_TO_SELL_PRIVATELY_IN_WINTER : EXPECTED_MONTHS_TO_SELL_PRIVATELY;
    }

    /** Below the range, lower half, upper half (midpoint included), above the range. */
    static OfferVerdict classify(Money amount, PriceRange wholesale) {
        if (amount.compareTo(wholesale.low()) < 0) {
            return OfferVerdict.LOW_BALL;
        }
        if (amount.compareTo(wholesale.midpoint()) < 0) {
            return OfferVerdict.BELOW_AVERAGE;
        }
        if (amount.compareTo(wholesale.high()) <= 0) {
            return OfferVerdict.FAIR;
        }
        return OfferVerdict.STRONG;
    }

    /**
     * Low-ball and below-average offers are countered at the top of the wholesale range;
     * fair offers halfway between the offer and the top; strong offers are accepted (null).
     */
    static Money counter(OfferVerdict verdict, Money amount, PriceRange wholesale) {
        return switch (verdict) {
            case LOW_BALL, BELOW_AVERAGE -> wholesale.high();
            case FAIR -> amount.plus(wholesale.high()).times(0.5).roundTo(100);
            case STRONG -> null;
        };
    }
}
