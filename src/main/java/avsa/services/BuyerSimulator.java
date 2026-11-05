package avsa.services;

import avsa.agent.AiResult;
import avsa.agent.AiTaskRunner;
import avsa.agent.ChatMessage;
import avsa.agent.MemoryManager;
import avsa.agent.tasks.BuyerTurn;
import avsa.agent.tasks.BuyerTurnTask;
import avsa.agent.tasks.CoachingDraft;
import avsa.agent.tasks.CoachingTask;
import avsa.domain.BuyerIntent;
import avsa.domain.ChatTurn;
import avsa.domain.CoachingReport;
import avsa.domain.GeneratedListing;
import avsa.domain.Money;
import avsa.domain.Persona;
import avsa.domain.SimulationSession;
import avsa.domain.SimulationStatus;
import avsa.domain.Speaker;
import avsa.domain.VehicleFacts;
import java.time.Clock;
import java.util.UUID;

/**
 * Runs a practice negotiation with an AI buyer (F09). Code decides each persona's opening offer
 * and ceiling, tracks the numbers, and ends the session; the AI only writes the buyer's words.
 */
public final class BuyerSimulator {

    private final AiTaskRunner ai;
    private final MemoryManager memory;
    private final Clock clock;

    public BuyerSimulator(AiTaskRunner ai, MemoryManager memory, Clock clock) {
        this.ai = ai;
        this.memory = memory;
        this.clock = clock;
    }

    /** @throws IllegalStateException the AI is unavailable; the simulator cannot run without it */
    public SimulationSession start(Persona persona, GeneratedListing listing, VehicleFacts facts, Money floor) {
        SimulationSession session = new SimulationSession(UUID.randomUUID(), persona,
                SimulationSession.DEFAULT_MAX_TURNS, listing.price(), floor, listing);
        buyerTurn(session, facts);
        return session;
    }

    public ChatTurn reply(SimulationSession session, VehicleFacts facts, String sellerText) {
        if (session.status() != SimulationStatus.ACTIVE) {
            throw new IllegalStateException("This practice negotiation has ended. Start a new one.");
        }
        restore(session);
        ChatTurn seller = new ChatTurn(Speaker.SELLER, sellerText, null, null, clock.instant());
        session.add(seller);
        memory.append(session.id(), new ChatMessage("seller", sellerText));
        return buyerTurn(session, facts);
    }

    public CoachingReport end(SimulationSession session, VehicleFacts facts) {
        if (session.status() == SimulationStatus.ACTIVE) {
            session.setStatus(SimulationStatus.ENDED);
        }
        NegotiationTracker tracker = tracker(session);
        AiResult<CoachingDraft> result = ai.run(new CoachingTask(session.persona(), facts, session.transcript()));
        CoachingReport report = new CoachingReport(result.value().disclosureFeedback(), tracker.floorFeedback(),
                result.value().redFlags() == null ? java.util.List.of() : result.value().redFlags(),
                !result.usedFallback());
        session.setCoaching(report);
        return report;
    }

    private ChatTurn buyerTurn(SimulationSession session, VehicleFacts facts) {
        restore(session);
        Money previous = session.latestBuyerOffer().orElse(null);
        Money opening = previous == null ? openingOffer(session) : null;
        AiResult<BuyerTurn> result = ai.run(new BuyerTurnTask(session.persona(), facts, listingText(session.listing()),
                memory.window(session.id()), opening, maxOffer(session), previous));
        if (result.usedFallback() || result.value() == null) {
            throw new IllegalStateException("The buyer simulator needs the AI, which is unavailable right now. "
                    + String.join(" ", result.notes()));
        }
        BuyerTurn b = result.value();
        ChatTurn turn = new ChatTurn(Speaker.BUYER, b.message().strip(),
                b.offerAmount() == null ? null : Money.cad(b.offerAmount().longValue()), b.intent(), clock.instant());
        session.add(turn);
        memory.append(session.id(), new ChatMessage("buyer", turn.message()
                + turn.offer().map(o -> " [offer " + o + "]").orElse("")));
        if (b.listingGap() != null && !b.listingGap().isBlank()) {
            session.addListingGap(b.listingGap().strip());
        }
        if (b.intent() == BuyerIntent.ACCEPT) {
            session.setStatus(SimulationStatus.AGREED);
        } else if (b.intent() == BuyerIntent.WALK_AWAY) {
            session.setStatus(SimulationStatus.BUYER_LEFT);
        } else if (session.buyerTurns() >= session.maxTurns()) {
            session.setStatus(SimulationStatus.TURN_LIMIT);
        }
        return turn;
    }

    /** A new run of the app starts with empty memory, so the conversation is reloaded from the transcript. */
    private void restore(SimulationSession session) {
        if (!memory.knows(session.id()) && !session.transcript().isEmpty()) {
            for (ChatTurn t : session.transcript()) {
                memory.append(session.id(), new ChatMessage(t.speaker().name().toLowerCase(), t.message()
                        + t.offer().map(o -> " [offer " + o + "]").orElse("")));
            }
        }
    }

    /** Each persona opens at a fixed share of the asking price. */
    static Money openingOffer(SimulationSession session) {
        double share = switch (session.persona()) {
            case SERIOUS -> 0.95;
            case LOW_BALLER -> 0.70;
            case DEALER -> 0.78;
            case SUSPICIOUS -> 1.05;
        };
        return session.askingPrice().times(share).roundTo(100);
    }

    /** The most each persona will pay: a dealer never goes above wholesale, a low-baller stops short of asking. */
    static Money maxOffer(SimulationSession session) {
        Money asking = session.askingPrice();
        return switch (session.persona()) {
            case SERIOUS -> asking;
            case LOW_BALLER -> session.floor().plus(asking.minus(session.floor()).times(0.5)).roundTo(100);
            case DEALER -> session.floor();
            case SUSPICIOUS -> asking.times(1.10).roundTo(100);
        };
    }

    private static NegotiationTracker tracker(SimulationSession session) {
        NegotiationTracker tracker = new NegotiationTracker(session.askingPrice(), session.floor());
        session.transcript().forEach(tracker::record);
        return tracker;
    }

    private static String listingText(GeneratedListing l) {
        return l.title() + " - " + l.price() + "\n" + l.description() + "\nCondition: " + l.conditionDisclosure()
                + (l.highlights().isEmpty() ? "" : "\nHighlights: " + String.join(", ", l.highlights()));
    }
}
