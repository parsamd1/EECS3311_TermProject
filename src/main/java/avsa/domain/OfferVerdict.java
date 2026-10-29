package avsa.domain;

/** Where an offer falls against the wholesale range (F11). */
public enum OfferVerdict {
    /** Below the wholesale range. */
    LOW_BALL,
    /** In the lower half of the wholesale range. */
    BELOW_AVERAGE,
    /** In the upper half of the wholesale range. */
    FAIR,
    /** Above the wholesale range. */
    STRONG
}
