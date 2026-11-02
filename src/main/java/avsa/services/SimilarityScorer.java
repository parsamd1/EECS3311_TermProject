package avsa.services;

import avsa.domain.ComparableListing;
import avsa.domain.FlagCode;
import avsa.domain.ListingAccidentStatus;
import avsa.domain.ListingFlag;
import avsa.domain.ScoreBreakdown;
import avsa.domain.VehicleHistory;
import avsa.domain.VehicleProfile;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Scores how similar a listing is to the seller's vehicle (F02). Each factor is rated from 0 to 1
 * and multiplied by its weight, so the breakdown shows exactly what produced the score.
 */
public final class SimilarityScorer {

    /** One model year apart costs a quarter of the year factor. */
    static final double YEAR_STEP = 0.25;
    /** A difference this large rates the odometer factor at zero. */
    static final double ODOMETER_RANGE_KM = 100_000;
    static final double DISTANCE_RANGE_KM = 300;
    static final double SAME_TRIM_FAMILY = 0.8;
    static final double OTHER_TRIM = 0.3;
    static final double NEUTRAL = 0.5;
    static final double HISTORY_DIFFERS = 0.2;
    private static final Pattern POOR_CONDITION =
            Pattern.compile("not drivable|as[- ]is|salvage|rebuilt", Pattern.CASE_INSENSITIVE);

    private final ScoringWeights weights;
    private final Map<FlagCode, Double> flagPenalties;

    public SimilarityScorer() {
        this(ScoringWeights.defaults(), defaultPenalties());
    }

    public SimilarityScorer(ScoringWeights weights, Map<FlagCode, Double> flagPenalties) {
        this.weights = weights;
        this.flagPenalties = flagPenalties;
    }

    private static Map<FlagCode, Double> defaultPenalties() {
        Map<FlagCode, Double> penalties = new EnumMap<>(FlagCode.class);
        penalties.put(FlagCode.SALVAGE_TITLE, 0.50);
        penalties.put(FlagCode.TRIM_MISMATCH, 0.20);
        penalties.put(FlagCode.LIKELY_DUPLICATE, 0.30);
        penalties.put(FlagCode.PRICE_TOO_LOW_TO_TRUST, 0.30);
        return penalties;
    }

    /** @param history may be null */
    public ScoreBreakdown score(VehicleProfile profile, VehicleHistory history, ComparableListing listing) {
        double year = Math.max(0, 1 - YEAR_STEP * Math.abs(listing.year() - profile.year()));
        double odometer = Math.max(0, 1 - Math.abs(listing.odometerKm() - profile.odometerKm()) / ODOMETER_RANGE_KM);
        double distance = listing.locationApproximate() ? NEUTRAL : Math.max(0, 1 - listing.distanceKm() / DISTANCE_RANGE_KM);
        return new ScoreBreakdown(weights.year() * year, weights.odometer() * odometer,
                weights.trim() * trimRating(profile.trim(), listing.trim()), weights.distance() * distance,
                weights.condition() * conditionRating(listing), weights.history() * historyRating(history, listing), 0);
    }

    /** True for the same trim or a variant of it: "GS Comfort" belongs to the GS family. */
    static boolean sameTrimFamily(String a, String b) {
        return !a.isBlank() && !b.isBlank() && firstWord(a).equals(firstWord(b));
    }

    private static String firstWord(String trim) {
        return trim.strip().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    private static double trimRating(String seller, String listing) {
        if (seller.isBlank() || listing.isBlank()) {
            return NEUTRAL;
        }
        if (seller.equalsIgnoreCase(listing)) {
            return 1;
        }
        return sameTrimFamily(seller, listing) ? SAME_TRIM_FAMILY : OTHER_TRIM;
    }

    /** Listings rarely describe their condition, so only an explicit warning sign lowers this factor. */
    private static double conditionRating(ComparableListing listing) {
        return POOR_CONDITION.matcher(listing.description()).find() ? 0 : 1;
    }

    private static double historyRating(VehicleHistory history, ComparableListing listing) {
        if (listing.brandedTitle()) {
            return 0;
        }
        if (history == null || !history.accidentsKnown() || listing.accidentStatus() == ListingAccidentStatus.UNKNOWN) {
            return NEUTRAL;
        }
        boolean listingHasAccident = listing.accidentStatus() == ListingAccidentStatus.REPORTED;
        return history.hasAccidents() == listingHasAccident ? 1 : HISTORY_DIFFERS;
    }

    /** Records the review and subtracts a fixed penalty per flag. The review itself never sets a score. */
    public void applyFlags(ComparableListing listing, List<ListingFlag> flags) {
        double penalty = flags.stream().mapToDouble(f -> flagPenalties.getOrDefault(f.code(), 0.0)).sum();
        listing.setReview(flags);
        listing.breakdown().ifPresent(b -> listing.setBreakdown(b.withFlagPenalty(penalty)));
    }
}
