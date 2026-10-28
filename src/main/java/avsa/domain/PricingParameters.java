package avsa.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Tunable pricing inputs (F03).
 *
 * @param negotiationMargin       how far below asking a sale usually closes (asking -> expected selling price)
 * @param dealerSpread            how far below retail dealers buy (retail -> wholesale)
 * @param odometerAdjustmentPerKm dollars per km used to align each comparable with the seller's odometer
 * @param aiAdjustmentCap         maximum size of one AI-proposed adjustment, as a fraction of the baseline
 * @param maxAiAdjustments        maximum number of AI-proposed adjustments
 */
public record PricingParameters(double negotiationMargin, double dealerSpread, double odometerAdjustmentPerKm,
                                double aiAdjustmentCap, int maxAiAdjustments) {

    /** Calibrated from the author's dealer offers: $23.5-25.5k against a $28-29k retail value (Stage 1, 1.7). */
    public static final double DEFAULT_DEALER_SPREAD = 0.14;
    /** Assumption, user-tunable: typical gap between asking and selling price. */
    public static final double DEFAULT_NEGOTIATION_MARGIN = 0.05;
    /** Assumption, user-tunable: value of one kilometre of odometer difference. */
    public static final double DEFAULT_ODOMETER_ADJUSTMENT_PER_KM = 0.05;

    public static PricingParameters defaults() {
        return new PricingParameters(DEFAULT_NEGOTIATION_MARGIN, DEFAULT_DEALER_SPREAD,
                DEFAULT_ODOMETER_ADJUSTMENT_PER_KM, 0.03, 3);
    }

    public PricingParameters withNegotiationMargin(double margin) {
        return new PricingParameters(margin, dealerSpread, odometerAdjustmentPerKm, aiAdjustmentCap, maxAiAdjustments);
    }

    public PricingParameters withDealerSpread(double spread) {
        return new PricingParameters(negotiationMargin, spread, odometerAdjustmentPerKm, aiAdjustmentCap, maxAiAdjustments);
    }

    public List<ValidationError> validate() {
        List<ValidationError> errors = new ArrayList<>();
        if (negotiationMargin < 0 || negotiationMargin >= 0.5) {
            errors.add(ValidationError.error("negotiationMargin", "Negotiation margin must be between 0% and 50%."));
        }
        if (dealerSpread < 0 || dealerSpread >= 1) {
            errors.add(ValidationError.error("dealerSpread", "Dealer spread must be at least 0% and below 100%."));
        }
        if (odometerAdjustmentPerKm < 0) {
            errors.add(ValidationError.error("odometerAdjustmentPerKm", "Odometer adjustment cannot be negative."));
        }
        if (aiAdjustmentCap < 0 || aiAdjustmentCap > 0.10) {
            errors.add(ValidationError.error("aiAdjustmentCap", "AI adjustment cap must be between 0% and 10%."));
        }
        if (maxAiAdjustments < 0) {
            errors.add(ValidationError.error("maxAiAdjustments", "Maximum AI adjustments cannot be negative."));
        }
        return errors;
    }
}
