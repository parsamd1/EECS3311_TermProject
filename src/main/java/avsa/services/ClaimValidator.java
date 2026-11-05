package avsa.services;

import avsa.domain.Claim;
import avsa.domain.ComparableListing;
import avsa.domain.PercentileMetric;
import avsa.domain.PositioningReport;
import avsa.domain.VehicleProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Removes positioning claims that the evidence does not support (F05): claims citing nothing or
 * something that does not exist, numbers that match neither the vehicle nor a cited comparable,
 * and comparisons that the cited comparables contradict.
 */
public final class ClaimValidator {

    private static final Pattern KILOMETRES = Pattern.compile("(\\d[\\d,]*)\\s?(?:km|kilometres|kilometers)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOLLARS = Pattern.compile("\\$\\s?(\\d[\\d,]*)");
    private static final Pattern LOWER_KM = Pattern.compile("(lower|fewer|less)\\s+(mileage|kilomet|km|odometer)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHEAPER = Pattern.compile("cheaper|lower price|priced (below|under|lower)|"
            + "less expensive|undercut", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEWER = Pattern.compile("\\bnewer\\b", Pattern.CASE_INSENSITIVE);
    private static final double PRICE_TOLERANCE = 100;

    public PositioningReport filter(PositioningReport report, List<ComparableListing> comps, VehicleProfile profile) {
        Map<String, ComparableListing> byId = comps.stream()
                .collect(Collectors.toMap(ComparableListing::listingId, Function.identity(), (a, b) -> a));
        List<String> metricRefs = report.metrics().stream().map(m -> "metric:" + m.name()).toList();
        double asking = report.metrics().stream().filter(m -> m.name().equals(PositioningAnalyzer.PRICE))
                .mapToDouble(PercentileMetric::value).findFirst().orElse(0);

        List<Claim> kept = new ArrayList<>();
        int removed = 0;
        for (Claim claim : report.claims()) {
            if (supported(claim, byId, metricRefs, profile, asking)) {
                kept.add(claim);
            } else {
                removed++;
            }
        }
        String justification = report.priceJustification();
        if (!justification.isBlank() && !numbersMatch(justification, comps, profile, asking)) {
            justification = "";
            removed++;
        }
        return new PositioningReport(report.metrics(), kept, justification, report.removedClaimCount() + removed,
                report.metricsOnly(), report.notices());
    }

    private static boolean supported(Claim claim, Map<String, ComparableListing> byId, List<String> metricRefs,
                                     VehicleProfile profile, double asking) {
        if (claim.evidenceRefs().isEmpty() || claim.text().isBlank()) {
            return false;
        }
        List<ComparableListing> cited = new ArrayList<>();
        for (String ref : claim.evidenceRefs()) {
            Optional<ComparableListing> comp = Optional.ofNullable(byId.get(ref));
            if (comp.isEmpty() && !metricRefs.contains(ref)) {
                return false;
            }
            comp.ifPresent(cited::add);
        }
        if (!numbersMatch(claim.text(), cited.isEmpty() ? List.copyOf(byId.values()) : cited, profile, asking)) {
            return false;
        }
        String text = claim.text();
        if (LOWER_KM.matcher(text).find() && cited.stream().anyMatch(c -> c.odometerKm() <= profile.odometerKm())) {
            return false;
        }
        if (CHEAPER.matcher(text).find() && cited.stream().anyMatch(c -> c.askingPrice().toDouble() <= asking)) {
            return false;
        }
        return !(NEWER.matcher(text).find() && cited.stream().anyMatch(c -> c.year() >= profile.year()));
    }

    /** Every kilometre and dollar figure must belong to the vehicle or to one of the given comparables. */
    private static boolean numbersMatch(String text, List<ComparableListing> comps, VehicleProfile profile,
                                        double asking) {
        Matcher km = KILOMETRES.matcher(text);
        while (km.find()) {
            int value = Integer.parseInt(km.group(1).replace(",", ""));
            if (value != profile.odometerKm() && comps.stream().noneMatch(c -> c.odometerKm() == value)) {
                return false;
            }
        }
        Matcher dollars = DOLLARS.matcher(text);
        while (dollars.find()) {
            double value = Double.parseDouble(dollars.group(1).replace(",", ""));
            boolean known = Math.abs(value - asking) <= PRICE_TOLERANCE || comps.stream()
                    .anyMatch(c -> Math.abs(c.askingPrice().toDouble() - value) <= PRICE_TOLERANCE);
            if (!known) {
                return false;
            }
        }
        return true;
    }
}
