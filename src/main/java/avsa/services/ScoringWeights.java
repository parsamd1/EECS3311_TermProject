package avsa.services;

/** How much each factor counts towards a listing's similarity score. The weights add up to 1. */
public record ScoringWeights(double year, double odometer, double trim, double distance, double condition,
                             double history) {

    public static ScoringWeights defaults() {
        return new ScoringWeights(0.25, 0.30, 0.20, 0.10, 0.05, 0.10);
    }
}
