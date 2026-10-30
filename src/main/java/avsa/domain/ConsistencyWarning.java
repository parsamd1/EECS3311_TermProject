package avsa.domain;

/** A disagreement between a vehicle history and the profile. A blocking one needs the seller's confirmation. */
public record ConsistencyWarning(WarningCode code, String message, boolean blocking) {
}
