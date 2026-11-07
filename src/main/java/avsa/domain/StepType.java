package avsa.domain;

/** The steps a guided workflow can use (F12); each one runs an existing feature. */
public enum StepType {
    FIND_COMPARABLES,
    RECOMMEND_PRICE,
    FORECAST,
    ANALYZE_POSITIONING,
    GENERATE_LISTING,
    EXPORT_LISTING
}
