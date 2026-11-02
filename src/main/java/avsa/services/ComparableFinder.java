package avsa.services;

import avsa.domain.ComparableListing;
import avsa.domain.ListingFlag;
import avsa.domain.Location;
import avsa.domain.MarketSnapshot;
import avsa.domain.MarketStats;
import avsa.domain.ValidationError;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.market.Gazetteer;
import avsa.infrastructure.market.ListingNormalizer;
import avsa.infrastructure.market.MarketDataSource;
import avsa.infrastructure.market.MarketQuery;
import avsa.infrastructure.market.MarketSearchResult;
import avsa.infrastructure.market.RawListing;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Finds and ranks comparable listings (F02): retrieves, normalizes, removes duplicates and
 * outliers, scores, and widens the search when too few remain.
 */
public final class ComparableFinder {

    static final int MIN_COMPARABLES = 3;
    static final int MAX_WIDENED_RADIUS_KM = 400;
    static final int MAX_WIDENED_YEAR_WINDOW = 4;

    private final MarketDataSource source;
    private final ListingNormalizer normalizer;
    private final Gazetteer gazetteer;
    private final OutlierFilter outlierFilter;
    private final SimilarityScorer scorer;
    private final Clock clock;

    public ComparableFinder(MarketDataSource source, ListingNormalizer normalizer, Gazetteer gazetteer,
                            OutlierFilter outlierFilter, SimilarityScorer scorer, Clock clock) {
        this.source = source;
        this.normalizer = normalizer;
        this.gazetteer = gazetteer;
        this.outlierFilter = outlierFilter;
        this.scorer = scorer;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException the profile is invalid; the seller must fix it first (F01) */
    public MarketSnapshot find(VehicleProfile profile, SearchRequest request) {
        List<ValidationError> errors = profile.validate(clock.instant().atZone(clock.getZone()).toLocalDate());
        if (VehicleProfile.hasBlockingErrors(errors)) {
            throw new IllegalArgumentException("Fix the vehicle profile first: "
                    + errors.stream().filter(ValidationError::blocking).findFirst().orElseThrow().message());
        }
        Location seller = profile.location();
        Location origin = gazetteer.resolve(seller.city(), seller.province()).orElse(seller);

        Map<MarketQuery, MarketSearchResult> fetched = new HashMap<>();
        SearchRequest current = request;
        Attempt attempt = attempt(profile, current, origin, fetched);
        while (attempt.listings.size() < MIN_COMPARABLES) {
            Optional<SearchRequest> wider = widen(current);
            if (wider.isEmpty()) {
                break;
            }
            current = wider.get();
            attempt = attempt(profile, current, origin, fetched);
        }
        return snapshot(profile, request, current, origin, attempt);
    }

    private record Attempt(List<ComparableListing> listings, MarketSearchResult result, int skipped) {
    }

    /**
     * The source is asked once for the widest model-year range a search can reach, and each attempt narrows it here,
     * so widening never pays for another live search.
     */
    private Attempt attempt(VehicleProfile profile, SearchRequest request, Location origin,
                            Map<MarketQuery, MarketSearchResult> fetched) {
        int widestWindow = Math.max(request.yearWindow(), MAX_WIDENED_YEAR_WINDOW);
        MarketQuery query = new MarketQuery(profile.make(), profile.model(), profile.year() - widestWindow,
                profile.year() + widestWindow, origin.city(), origin.province());
        MarketSearchResult result = fetched.computeIfAbsent(query, source::search);
        int skipped = result.skippedRecords();
        Set<String> seen = new HashSet<>();
        List<ComparableListing> nearby = new ArrayList<>();
        for (RawListing raw : result.listings()) {
            Optional<ComparableListing> listing = normalizer.normalize(raw, origin);
            if (listing.isEmpty()) {
                skipped++;
            } else if (Math.abs(listing.get().year() - profile.year()) <= request.yearWindow()
                    && seen.add(listing.get().listingId()) && listing.get().distanceKm() <= request.radiusKm()
                    && matchesTrim(profile, request, listing.get())) {
                nearby.add(listing.get());
            }
        }
        return new Attempt(outlierFilter.removeOutliers(nearby), result, skipped);
    }

    private static boolean matchesTrim(VehicleProfile profile, SearchRequest request, ComparableListing listing) {
        return !request.requireTrim() || profile.trim().isBlank()
                || SimilarityScorer.sameTrimFamily(profile.trim(), listing.trim());
    }

    /** The next wider search: first the radius, then the model years, then any trim. */
    private static Optional<SearchRequest> widen(SearchRequest request) {
        if (request.radiusKm() < MAX_WIDENED_RADIUS_KM) {
            return Optional.of(request.withRadius(Math.min(request.radiusKm() * 2, MAX_WIDENED_RADIUS_KM)));
        }
        if (request.yearWindow() < MAX_WIDENED_YEAR_WINDOW) {
            return Optional.of(request.withYearWindow(request.yearWindow() + 1));
        }
        return request.requireTrim() ? Optional.of(request.withoutTrimRequirement()) : Optional.empty();
    }

    private MarketSnapshot snapshot(VehicleProfile profile, SearchRequest asked, SearchRequest used, Location origin,
                                    Attempt attempt) {
        for (ComparableListing listing : attempt.listings) {
            listing.setBreakdown(scorer.score(profile, used.history(), listing));
        }
        List<ComparableListing> ranked = rank(attempt.listings).stream().limit(used.maxResults()).toList();

        MarketSearchResult result = attempt.result;
        List<String> warnings = new ArrayList<>();
        if (result.fromCache()) {
            warnings.add("The data source was unavailable; showing listings retrieved at " + result.retrievedAt() + ".");
        } else if (!result.sourceName().equals(source.name())) {
            warnings.add(source.name() + " was unavailable; using: " + result.sourceName() + ".");
        }
        if (!used.equals(asked)) {
            warnings.add("Too few listings matched, so the search was widened (radius " + used.radiusKm() + " km, "
                    + "model years +/-" + used.yearWindow() + (used.requireTrim() ? "" : ", any trim") + ").");
        }
        boolean lowConfidence = ranked.size() < MIN_COMPARABLES;
        if (lowConfidence) {
            warnings.add("Fewer than " + MIN_COMPARABLES + " comparable listings were found. "
                    + "Importing a CSV of listings may help.");
        }
        if (attempt.skipped > 0) {
            warnings.add(attempt.skipped + " listing records were skipped because they were incomplete or could not be verified.");
        }
        return new MarketSnapshot(UUID.randomUUID(), profile.id(), clock.instant(), result.sourceName(),
                result.fromCache(), result.retrievedAt(), used.radiusKm(), lowConfidence, attempt.skipped, ranked,
                MarketStats.of(ranked), warnings);
    }

    /** Applies the listing review: only the fixed penalties change scores. Unlisted listings were reviewed clean. */
    public MarketSnapshot applyReview(MarketSnapshot snapshot, Map<String, List<ListingFlag>> flags) {
        for (ComparableListing listing : snapshot.rankedListings()) {
            scorer.applyFlags(listing, flags.getOrDefault(listing.listingId(), List.of()));
        }
        return snapshot.withListings(rank(snapshot.rankedListings()));
    }

    private static List<ComparableListing> rank(List<ComparableListing> listings) {
        return listings.stream().sorted(Comparator.comparingDouble(ComparableListing::score).reversed()
                .thenComparing(ComparableListing::listingId)).toList();
    }
}
