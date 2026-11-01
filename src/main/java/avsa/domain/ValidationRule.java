package avsa.domain;

/** The honesty and accuracy rules every listing is checked against (F07). */
public enum ValidationRule {
    DEFECT_NOT_DISCLOSED,
    BANNED_CLAIM,
    FACT_MISMATCH,
    LENGTH_OUT_OF_BOUNDS
}
