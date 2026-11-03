package avsa.agent;

import avsa.domain.ComparableListing;
import avsa.infrastructure.market.MarketQuery;
import java.util.List;

/**
 * The only class that builds prompts. Each feature's prompt is added here as that feature's
 * AI part is built; facts reach the model only through redacted {@code VehicleFacts}.
 */
public final class PromptComposer {

    /** Canadian marketplaces that list individual used vehicles. */
    public static final List<String> MARKETPLACE_DOMAINS = List.of("autotrader.ca", "kijiji.ca", "cargurus.ca");
    static final int MAX_SEARCHES = 5;
    static final int MAX_FETCHES = 4;
    /** About 30,000 tokens is a long results page; at Haiku 5.5 prices four of them cost about a cent. */
    static final int MAX_FETCH_TOKENS = 30_000;
    static final int MAX_LISTINGS = 20;
    /** Room for the model's reasoning over many pages plus twenty listings of JSON. */
    static final long SEARCH_MAX_TOKENS = 32_000;

    private static final String LISTING_SEARCH_SYSTEM = """
            You research the Canadian used-vehicle market for a private seller. Use web search to find \
            vehicles that are currently for sale, then open (fetch) the most promising search-results pages \
            and listing pages to read the individual listings on them.

            Report only listings you actually read on a page you searched or fetched. Copy every number exactly \
            as the listing shows it; never estimate, round or invent a listing, a price or an odometer reading. \
            Each "url" must be the page where you read the listing, exactly as it appeared in your search or \
            fetch results: the listing's own page if you opened it, otherwise the results page that shows it. \
            Include the listing's city when the page shows it. When a listing does not show a value, use null.

            After searching, reply with only this JSON object and no other text:
            {"listings": [{"source": "AutoTrader", "url": "https://...", "year": 2022, "make": "Mazda", \
            "model": "CX-5", "trim": "GS", "odometerKm": 49000, "askingPrice": 28900, "city": "Toronto", \
            "province": "ON", "accidents": "what the listing says about accidents, or null", \
            "description": "one short sentence from the listing"}]}
            """;

    /** A search of the marketplaces for listings that match the query (F02 live search). */
    public LLMRequest listingSearch(MarketQuery query) {
        String user = "Find used " + query.make() + " " + query.model() + " vehicles from model years "
                + query.minYear() + " to " + query.maxYear() + " that are for sale near " + query.city() + ", "
                + query.province() + ", Canada. Prefer individual listing pages on " + String.join(", ", MARKETPLACE_DOMAINS)
                + ". Return up to " + MAX_LISTINGS + " listings.";
        return new LLMRequest("listing-search", LISTING_SEARCH_SYSTEM, user, SEARCH_MAX_TOKENS,
                new WebSearchOptions(MAX_SEARCHES, MARKETPLACE_DOMAINS, query.city(), query.province(), "CA",
                        MAX_FETCHES, MAX_FETCH_TOKENS));
    }

    private static final String COMP_REVIEW_SYSTEM = """
            You check used-vehicle listings that will be used as price comparables. Read each listing's text \
            and report only real problems, using exactly one of these codes:
            SALVAGE_TITLE: the text mentions a salvage, rebuilt or branded title, or flood or fire damage.
            TRIM_MISMATCH: the text shows a different trim from the one given for the listing.
            LIKELY_DUPLICATE: the listing appears to be the same vehicle as another listing in the list.
            PRICE_TOO_LOW_TO_TRUST: the price is far below the others with no explanation, or looks like a deposit, \
            a monthly payment or a placeholder.
            Most listings have no problem; do not flag them. Use only the listing ids given.
            Reply with only this JSON object: {"flags": [{"listingId": "...", "code": "TRIM_MISMATCH", \
            "reason": "one short sentence"}]}
            """;

    /** A review of comparable listings for problems numbers cannot reveal (F02). */
    public LLMRequest compReview(List<ComparableListing> listings, String sellerTrim) {
        StringBuilder user = new StringBuilder("The seller's trim is \"").append(sellerTrim)
                .append("\". Review these listings:\n");
        for (ComparableListing l : listings) {
            user.append("- id ").append(l.listingId()).append(": ").append(l.year()).append(' ').append(l.trim())
                    .append(", ").append(l.odometerKm()).append(" km, asking ").append(l.askingPrice())
                    .append(", from ").append(l.source()).append(". Text: ").append(l.description()).append('\n');
        }
        return new LLMRequest("comp-review", COMP_REVIEW_SYSTEM, user.toString(), 4_000, null);
    }

    /** A one-word request used by the pre-demo check. */
    public LLMRequest connectionCheck() {
        return new LLMRequest("connection-check", "Reply with exactly one word.", "Reply with OK.", 1_000, null);
    }

    /** A follow-up that tells the model what was wrong with its last answer. */
    public LLMRequest repair(LLMRequest request, List<String> errors) {
        StringBuilder feedback = new StringBuilder(request.user())
                .append("\n\nYour previous answer was rejected for these reasons:\n");
        for (String error : errors) {
            feedback.append("- ").append(error).append('\n');
        }
        feedback.append("Answer again and fix every one of them.");
        return request.withUser(feedback.toString());
    }
}
