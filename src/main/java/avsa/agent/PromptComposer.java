package avsa.agent;

import avsa.domain.ComparableListing;
import avsa.domain.ListingOptions;
import avsa.domain.Money;
import avsa.domain.OfferEvaluation;
import avsa.domain.PriceRecommendation;
import avsa.domain.VehicleFacts;
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

    private static final String PRICE_EXPLANATION_SYSTEM = """
            You explain a used-vehicle price to a private seller in Canada, in plain language, in at most five \
            sentences. The numbers were computed by a pricing engine and are final: explain them, never change \
            them, and do not state any dollar amount that is not in the figures you are given.

            You may also propose up to three adjustments for things the engine cannot know, such as included \
            winter tires, a recent major service or aftermarket parts, using only the vehicle facts given. Each is a \
            percent of the baseline between -3 and 3. Do not propose adjustments for odometer, condition, defects, \
            accidents, owners or history: those are already counted. Propose none if nothing applies.

            Reply with only this JSON object:
            {"explanation": "...", "adjustments": [{"label": "Winter tires included", "percent": 1.0, \
            "reason": "one short sentence"}]}
            """;

    /** An explanation of computed prices, with optional bounded adjustment proposals (F03). */
    public LLMRequest pricingExplanation(PriceRecommendation rec, VehicleFacts facts, int comparableCount) {
        StringBuilder user = new StringBuilder("Vehicle: ").append(facts.vehicleName()).append(", ")
                .append(facts.odometerKm()).append(" km, in ").append(facts.city()).append(", ").append(facts.province())
                .append(".\nFeatures: ").append(facts.features().isEmpty() ? "none listed" : String.join(", ",
                        facts.features()))
                .append("\nMaintenance records: ").append(facts.maintenanceRecords().size())
                .append("\nStrategy: ").append(rec.strategy().name().toLowerCase().replace('_', ' '))
                .append("\nComparable listings used: ").append(comparableCount)
                .append("\nBaseline: ").append(rec.baseline())
                .append("\nRetail range: ").append(rec.retail())
                .append("\nWholesale range: ").append(rec.wholesale())
                .append("\nAsking price: ").append(rec.askingPrice())
                .append("\nWalk-away floor: ").append(rec.walkAwayFloor())
                .append("\nConfidence: ").append(rec.confidence().name().toLowerCase());
        rec.adjustments().forEach(a -> user.append("\nAdjustment: ").append(a.label()).append(' ').append(a.amount()));
        rec.warnings().forEach(w -> user.append("\nWarning: ").append(w));
        return new LLMRequest("price-explanation", PRICE_EXPLANATION_SYSTEM, user.toString(), 8_000, null);
    }

    private static final String LISTING_SYSTEM = """
            You write an honest used-vehicle listing for a private seller in Canada. Use only the facts given; \
            never invent features, service history or condition. The condition disclosure must name every item \
            in "Must disclose" in plain words, including its type and location. Do not use these claims: \
            flawless, no issues, no problems, perfect condition, mint condition, like new; and do not claim the \
            vehicle is accident-free unless the facts say the accident history is confirmed clean. State the year, \
            make and model in the title, and the exact odometer reading in the description. Write the price only \
            as the exact asking price given, or not at all. The description must be between 100 and 4000 \
            characters.

            Reply with only this JSON object:
            {"title": "...", "description": "...", "highlights": ["..."], "conditionDisclosure": "...", \
            "seoKeywords": ["..."]}
            """;

    /** A listing draft from redacted facts, with the defects that must be disclosed (F07). */
    public LLMRequest listing(VehicleFacts facts, ListingOptions options) {
        StringBuilder user = new StringBuilder("Facts (do not change them):")
                .append("\n- Vehicle: ").append(facts.vehicleName())
                .append("\n- Odometer: ").append(String.format(java.util.Locale.ROOT, "%,d", facts.odometerKm())).append(" km")
                .append("\n- Body: ").append(lower(facts.bodyStyle())).append(", transmission: ").append(lower(facts.transmission()))
                .append(", fuel: ").append(lower(facts.fuelType()))
                .append("\n- Location: ").append(facts.city()).append(", ").append(facts.province())
                .append("\n- Asking price: ").append(facts.price());
        if (options.includeFeatures() && !facts.features().isEmpty()) {
            user.append("\n- Features: ").append(String.join(", ", facts.features()));
        }
        if (options.includeMaintenance() && !facts.maintenanceRecords().isEmpty()) {
            facts.maintenanceRecords().forEach(r -> user.append("\n- Service: ").append(r.date()).append(' ')
                    .append(r.description()));
        }
        if (facts.ownerCount() != null) {
            user.append("\n- Owners: ").append(facts.ownerCount());
        }
        user.append("\n- Accident history: ").append(!facts.accidentsKnown() ? "not confirmed"
                : facts.accidentsReported() == 0 ? "confirmed, no reported accidents"
                : facts.accidentsReported() + " reported accident(s)");
        user.append("\nMust disclose:");
        if (facts.defects().isEmpty() && facts.accidentsReported() == 0 && !facts.lienOutstanding()) {
            user.append(" nothing recorded; say the seller has not recorded any defects.");
        }
        facts.defects().forEach(d -> user.append("\n- ").append(d.severity().name().toLowerCase()).append(' ')
                .append(d.type().name().toLowerCase()).append(d.bodyLocation().isEmpty() ? "" : " on the "
                + d.bodyLocation()).append(d.note().isEmpty() ? "" : " (" + d.note() + ")"));
        if (facts.accidentsKnown() && facts.accidentsReported() > 0) {
            user.append("\n- the reported accident history");
        }
        if (facts.lienOutstanding()) {
            user.append("\n- an outstanding lien, to be cleared at sale");
        }
        user.append("\nTone: ").append(options.tone().name().toLowerCase());
        if (!options.keywords().isEmpty()) {
            user.append("\nInclude these search keywords where natural: ").append(String.join(", ", options.keywords()));
        }
        return new LLMRequest("listing-draft", LISTING_SYSTEM, user.toString(), 8_000, null);
    }

    private static String lower(Enum<?> value) {
        return value == null ? "unknown" : value.name().toLowerCase();
    }

    private static final String COUNTER_SCRIPT_SYSTEM = """
            You help a private seller in Canada reply to an offer on their used vehicle. Write what the seller \
            can say or send to the buyer: polite, firm, under 150 words, in the first person. If the buyer gave \
            reasons for their price, answer them briefly, citing the comparable listings given. Use only the \
            dollar figures given; never invent a number. If a counter amount is given, ask for exactly that \
            amount. If the verdict is strong, accept the offer instead of countering.
            Reply with only the script text.
            """;

    /** A counter-offer script for an evaluated offer (F11). */
    public LLMRequest counterScript(OfferEvaluation e, PriceRecommendation price, List<ComparableListing> comparables) {
        StringBuilder user = new StringBuilder("Offer: ").append(e.offer().amount()).append(" from a ")
                .append(e.offer().buyerType().name().toLowerCase().replace('_', ' '))
                .append("\nVerdict: ").append(e.verdict().name().toLowerCase().replace('_', ' '))
                .append("\nCounter amount: ").append(e.counterAmount().map(Money::toString).orElse("none, accept"))
                .append("\nWholesale range (what dealers pay): ").append(price.wholesale())
                .append("\nRetail range (what private buyers pay): ").append(price.retail())
                .append("\nAsking price: ").append(price.askingPrice());
        if (!e.offer().notes().isBlank()) {
            user.append("\nThe buyer said: ").append(e.offer().notes());
        }
        comparables.stream().limit(3).forEach(c -> user.append("\nComparable: ").append(c.year()).append(' ')
                .append(c.trim()).append(", ").append(c.odometerKm()).append(" km, listed at ").append(c.askingPrice()));
        return new LLMRequest("counter-script", COUNTER_SCRIPT_SYSTEM, user.toString(), 4_000, null);
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
