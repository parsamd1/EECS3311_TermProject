package avsa.domain;

/** Severity of a defect or accident; the weight feeds the damage severity score. */
public enum Severity {
    MINOR(1), MODERATE(3), SEVERE(6);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
