package avsa.infrastructure.market;

import avsa.agent.AiResult;
import avsa.agent.AiTaskRunner;
import avsa.agent.tasks.FoundListing;
import avsa.agent.tasks.ListingSearchResult;
import avsa.agent.tasks.ListingSearchTask;
import java.time.Clock;
import java.util.List;

/**
 * Searches online marketplaces through the AI's web search (F02). Only listings whose page
 * came from the search results are kept; the rest are counted as skipped. When the search
 * fails, the caching proxy falls back to cached or seeded data.
 */
public final class LiveSearchSource implements MarketDataSource {

    private final AiTaskRunner runner;
    private final Clock clock;

    /** A source that is not configured, for when no API key is available. */
    public LiveSearchSource() {
        this(null, Clock.systemUTC());
    }

    public LiveSearchSource(AiTaskRunner runner, Clock clock) {
        this.runner = runner;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "Live search";
    }

    @Override
    public boolean isAvailable() {
        return runner != null;
    }

    @Override
    public MarketSearchResult search(MarketQuery query) {
        if (runner == null) {
            throw new MarketDataUnavailableException("Live search is not configured. Add an API key to .env.", null);
        }
        AiResult<ListingSearchResult> result = runner.run(new ListingSearchTask(query));
        if (result.usedFallback()) {
            throw new MarketDataUnavailableException("Live search did not return usable listings: "
                    + String.join(" ", result.notes()), null);
        }
        List<FoundListing> verified = ListingSearchTask.verified(result.value(), result.sources());
        List<RawListing> listings = verified.stream().map(f -> toRaw(f, query)).filter(query::matches).toList();
        int unverified = result.value().listings().size() - verified.size();
        return new MarketSearchResult(name(), listings, unverified, false, clock.instant());
    }

    /**
     * A listing whose page did not show its city was found by a search near the seller, so it is placed in the
     * searched city and says so in its description. A listing read from a search-results page that reports the
     * searched city may only be repeating the search filter, so its location also counts as approximate.
     */
    private static RawListing toRaw(FoundListing f, MarketQuery query) {
        boolean located = f.city() != null && !f.city().isBlank();
        boolean fromFilter = located && isResultsPage(text(f.url())) && f.city().strip().equalsIgnoreCase(query.city());
        String note = !located ? " (Location not shown; assumed near " + query.city() + ".)"
                : fromFilter ? " (Read from a search-results page; location approximate.)" : "";
        return new RawListing(text(f.source()), text(f.url()), "", text(f.make()), text(f.model()), number(f.year()),
                text(f.trim()), number(f.odometerKm()), number(f.askingPrice()), located ? f.city() : query.city(),
                located && f.province() != null ? f.province() : query.province(), "", text(f.accidents()),
                (text(f.description()) + note).strip(), !located || fromFilter);
    }

    /** A marketplace page listing many vehicles, rather than one vehicle's own page. */
    public static boolean isResultsPage(String url) {
        String u = url.toLowerCase(java.util.Locale.ROOT);
        if (u.contains("kijiji.ca")) {
            return u.contains("/b-");
        }
        if (u.contains("autotrader.ca")) {
            return u.contains("/cars/");
        }
        if (u.contains("cargurus.ca")) {
            return !u.contains("listing=") && !u.contains("/details/");
        }
        return false;
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }

    private static String number(Integer n) {
        return n == null ? "" : n.toString();
    }
}
