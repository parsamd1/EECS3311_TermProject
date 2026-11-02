package avsa.agent;

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
