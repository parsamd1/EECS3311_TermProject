package avsa.domain;

/**
 * How a listing's similarity score was reached: each factor's weighted share of the
 * score, minus the fixed penalties for any flags. Lets the seller see why it ranks where it does.
 */
public record ScoreBreakdown(double year, double odometer, double trim, double distance, double condition,
                             double history, double flagPenalty) {

    /** The score in [0, 1]. */
    public double total() {
        double sum = year + odometer + trim + distance + condition + history - flagPenalty;
        return Math.max(0, Math.min(1, sum));
    }

    public ScoreBreakdown withFlagPenalty(double penalty) {
        return new ScoreBreakdown(year, odometer, trim, distance, condition, history, penalty);
    }
}
