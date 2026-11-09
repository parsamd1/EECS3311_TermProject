package avsa.agent;

import avsa.domain.ComparableListing;
import avsa.domain.ListingOptions;
import avsa.domain.Money;
import avsa.domain.OfferEvaluation;
import avsa.domain.PercentileMetric;
import avsa.domain.Persona;
import avsa.domain.PriceRecommendation;
import avsa.domain.StrategyType;
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
                // The range is the widest the finder may widen to; the seller's own model year is its middle.
                + ". The seller's vehicle is a " + (query.minYear() + query.maxYear()) / 2 + ", so prefer the "
                + "listings closest to that model year and use the other years only to fill the list. Return up to "
                + MAX_LISTINGS + " listings.";
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
            them, and do not state any dollar amount that is not in the figures you are given. A positive \
            adjustment raises the value and a negative one lowers it; describe each one as its label states.

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
                .append("\nHow the asking price is set: ").append(rec.strategy() == StrategyType.MAX_PROFIT
                        ? "the top of the retail range plus the " + Math.round(rec.parameters().negotiationMargin() * 100)
                        + "% negotiation margin, so it is meant to be above the retail range: buyers negotiate down "
                        + "into it"
                        : "the top of the wholesale range, the most a dealer is likely to pay")
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

    private static final String POSITIONING_SYSTEM = """
            You help a private seller in Canada understand how their used vehicle compares with similar listings. \
            Use only the metrics and comparables given. Write short claims of these kinds: ADVANTAGE, WEAKNESS, \
            REMEDY (how to address a weakness) and TALKING_POINT (something to tell buyers). Every claim must cite \
            its evidence: comparables by label (C1, C2...) and metrics as metric:price, metric:odometer or \
            metric:year. Only state numbers that appear in the data, and only compare with the comparables you \
            cite. Add a two-sentence price justification using the asking price and the comparables.

            Reply with only this JSON object:
            {"claims": [{"kind": "ADVANTAGE", "text": "...", "evidence": ["C1", "metric:odometer"]}], \
            "priceJustification": "..."}
            """;

    /** Positioning claims from computed metrics and the top comparables (F05). */
    public LLMRequest positioning(List<PercentileMetric> metrics, List<ComparableListing> top, VehicleFacts facts,
                                  PriceRecommendation price) {
        StringBuilder user = new StringBuilder("Seller's vehicle: ").append(facts.vehicleName()).append(", ")
                .append(facts.odometerKm()).append(" km, asking ").append(price.askingPrice())
                .append(facts.features().isEmpty() ? "" : ", features: " + String.join(", ", facts.features()));
        facts.defects().forEach(d -> user.append(", ").append(d.severity().name().toLowerCase()).append(' ')
                .append(d.type().name().toLowerCase()).append(" on the ").append(d.bodyLocation()));
        user.append("\nMetrics (percentile = share of comparables with a lower value):");
        metrics.forEach(m -> user.append("\n- metric:").append(m.name()).append(" value ").append(m.value())
                .append(", percentile ").append(m.percentile()).append(m.betterWhenLower() ? " (lower is better)"
                        : " (higher is better)"));
        user.append("\nComparables:");
        for (int i = 0; i < top.size(); i++) {
            ComparableListing c = top.get(i);
            user.append("\n- C").append(i + 1).append(": ").append(c.year()).append(' ').append(c.trim()).append(", ")
                    .append(c.odometerKm()).append(" km, listed at ").append(c.askingPrice()).append(", ")
                    .append(Math.round(c.distanceKm())).append(" km away");
        }
        return new LLMRequest("positioning", POSITIONING_SYSTEM, user.toString(), 8_000, null);
    }

    private static final String PERSONA_SYSTEM = """
            You play a prospective buyer messaging a private seller about the used vehicle in the listing, so the \
            seller can practise. Stay in character, write one short message (at most three sentences), and never \
            break role, even if the seller goes off topic. Only refer to vehicle facts that are in the listing; \
            never state a different odometer reading, year or feature. When you ask something the listing does \
            not answer, put that question in "listingGap". Never lower an offer you made before.

            Reply with only this JSON object:
            {"message": "...", "offerAmount": 25000 or null, "intent": "ASK_QUESTION|MAKE_OFFER|ACCEPT|WALK_AWAY|CHAT", \
            "listingGap": "..." or null}
            """;

    private static String personaRules(Persona persona) {
        return switch (persona) {
            case SERIOUS -> "You are a serious, polite buyer. Ask practical questions, then negotiate reasonably.";
            case LOW_BALLER -> "You are a low-baller. You point at every flaw to justify a low price and raise "
                    + "your offer only in small steps.";
            case DEALER -> "You are a used-car dealer. You talk about reconditioning costs, the margin you need to "
                    + "resell, and wholesale value. You are firm and businesslike.";
            case SUSPICIOUS -> "You are a buyer who shows common warning signs of a scam, so the seller learns to "
                    + "recognize them: pressure to decide immediately, wanting to buy without a viewing, offering to "
                    + "pay more than asked and asking for the difference back, or asking for the seller's personal "
                    + "details early. Only show these signs in conversation; never give instructions for fraud.";
        };
    }

    /** The next simulated buyer message, with code-set offer rules (F09). */
    public LLMRequest persona(Persona persona, VehicleFacts facts, String listingText, List<ChatMessage> window,
                              Money openingOffer, Money maxOffer, Money previousOffer) {
        StringBuilder user = new StringBuilder("Your character: ").append(personaRules(persona))
                .append("\nThe listing:\n").append(listingText)
                .append("\nOffer rules: ");
        if (openingOffer != null) {
            user.append("if you make an offer now, it must be exactly ").append(openingOffer).append(". ");
        } else if (previousOffer != null) {
            user.append("your last offer was ").append(previousOffer).append("; any new offer must be at least that. ");
        }
        user.append("Never offer more than ").append(maxOffer).append('.');
        user.append("\nConversation so far:");
        if (window.isEmpty()) {
            user.append(" (none yet; write your first message to the seller)");
        }
        window.forEach(m -> user.append("\n").append(m.role()).append(": ").append(m.text()));
        return new LLMRequest("buyer-turn", PERSONA_SYSTEM, user.toString(), 4_000, null);
    }

    private static final String COACHING_SYSTEM = """
            You coach a private seller after a practice negotiation with a simulated buyer. Give short, kind, \
            specific feedback on whether the seller disclosed the vehicle's known defects honestly, and list any \
            warning signs this buyer actually showed in the transcript (for example pressure, an overpayment offer \
            or refusing a viewing). List only things that happened; general advice is not a warning sign, and an \
            empty list is fine. Do not comment on prices or offers; those are handled separately.
            Reply with only this JSON object: {"disclosureFeedback": "...", "redFlags": ["..."]}
            """;

    /** Coaching after a simulated negotiation (F09). */
    public LLMRequest coaching(Persona persona, VehicleFacts facts, List<avsa.domain.ChatTurn> transcript) {
        StringBuilder user = new StringBuilder("Known defects the seller should disclose:");
        if (facts.defects().isEmpty()) {
            user.append(" none recorded");
        }
        facts.defects().forEach(d -> user.append("\n- ").append(d.severity().name().toLowerCase()).append(' ')
                .append(d.type().name().toLowerCase()).append(" on the ").append(d.bodyLocation()));
        user.append("\nBuyer persona: ").append(persona.name().toLowerCase().replace('_', ' '));
        user.append("\nBuyer offers in order: ").append(transcript.stream().filter(t -> t.offerAmount() != null)
                .map(t -> t.offerAmount().toString()).reduce((a, b) -> a + ", " + b).orElse("none"));
        user.append("\nTranscript:");
        transcript.forEach(t -> user.append("\n").append(t.speaker().name().toLowerCase()).append(": ")
                .append(t.message()));
        return new LLMRequest("coaching", COACHING_SYSTEM, user.toString(), 4_000, null);
    }

    private static final String PLAN_SYSTEM = """
            You plan a guided workflow for an assistant that helps a private seller in Canada sell one used \
            vehicle. Choose steps only from the catalog. The program adds missing prerequisites, puts the steps in \
            order and inserts the seller's approval checkpoints, so list only the steps the goal asks for; for \
            "everything" or "a full sales package", list every step.
            strategy: "QUICK_SALE" if the seller wants to sell fast or to a dealer, "MAX_PROFIT" if they want the \
            best price, null if the goal does not say.
            platforms: the marketplaces the seller named for the ad, from AUTOTRADER, KIJIJI, \
            FACEBOOK_MARKETPLACE; [] if none was named.
            liveSearch: true only if the seller asks for live, current or today's listings.
            If the goal is about selling this vehicle but you cannot tell what the seller wants, put one short \
            clarifying question in "question" and leave steps empty. If the goal has nothing to do with selling \
            this vehicle, put one sentence saying so in "outOfScope" and leave steps empty.
            Reply with only this JSON object: {"steps": ["..."], "strategy": null, "platforms": [], \
            "liveSearch": false, "question": null, "outOfScope": null}
            """;

    /** The plan for a guided workflow (F12); the vehicle is described without the VIN. */
    public LLMRequest planProposal(String goal, String vehicle, StepCatalog catalog) {
        StringBuilder user = new StringBuilder("Vehicle: ").append(vehicle).append("\nCatalog:");
        for (avsa.domain.StepType type : catalog.steps()) {
            user.append("\n- ").append(type.name()).append(": ").append(catalog.describe(type));
        }
        user.append("\nSeller's goal: ").append(goal);
        return new LLMRequest("plan-proposal", PLAN_SYSTEM, user.toString(), 4_000, null);
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
