package avsa.services;

import avsa.domain.DepreciationForecast;
import avsa.domain.ForecastPoint;
import avsa.domain.Money;
import avsa.domain.Offer;
import avsa.domain.OfferEvaluation;
import avsa.domain.OfferVerdict;
import avsa.domain.PriceRange;
import avsa.domain.PriceRecommendation;
import java.util.ArrayList;
import java.util.List;

/**
 * Judges an offer against the wholesale range with fixed rules (F11).
 */
public final class OfferEvaluator {

    /** Assumed time to find a private buyer when comparing an offer with waiting for retail. */
    static final int EXPECTED_MONTHS_TO_SELL_PRIVATELY = 3;

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
            netIfWaiting = forecast.pointAt(EXPECTED_MONTHS_TO_SELL_PRIVATELY)
                    .map(ForecastPoint::netIfSoldThen).orElse(null);
        }
        return new OfferEvaluation(offer, verdict, wholesale.positionOf(amount), wholesale.high().minus(amount),
                counter(verdict, amount, wholesale), netIfWaiting,
                amount.compareTo(rec.walkAwayFloor()) < 0, aboveRetail, warnings);
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
