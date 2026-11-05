package avsa.domain;

import java.util.List;
import java.util.Objects;

/**
 * How the vehicle compares with its competition (F05): the computed metrics, and the AI's claims
 * that survived validation.
 *
 * @param removedClaimCount claims dropped because their evidence was missing or contradicted the numbers
 * @param metricsOnly       true when the AI was unavailable and only the metrics are shown
 */
public record PositioningReport(List<PercentileMetric> metrics, List<Claim> claims, String priceJustification,
                                int removedClaimCount, boolean metricsOnly, List<String> notices) {

    public PositioningReport {
        metrics = List.copyOf(metrics);
        claims = List.copyOf(claims);
        priceJustification = Objects.requireNonNullElse(priceJustification, "");
        notices = List.copyOf(notices);
    }

    public List<Claim> claims(ClaimKind kind) {
        return claims.stream().filter(c -> c.kind() == kind).toList();
    }
}
