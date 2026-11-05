package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.ChatMessage;
import avsa.agent.LLMRequest;
import avsa.agent.PromptComposer;
import avsa.domain.BuyerIntent;
import avsa.domain.Money;
import avsa.domain.Persona;
import avsa.domain.VehicleFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the AI for the next buyer message (F09). Code sets the opening offer and the most the
 * persona will pay, and checks that the buyer never lowers an offer and never states vehicle
 * facts that differ from the profile.
 */
public final class BuyerTurnTask implements AiTask<BuyerTurn> {

    private static final Pattern KILOMETRES = Pattern.compile("(\\d[\\d,]*)\\s?(?:km|kilometres|kilometers)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL_YEAR = Pattern.compile("\\b((?:19|20)\\d{2})\\s+[A-Z]");

    private final Persona persona;
    private final VehicleFacts facts;
    private final String listingText;
    private final List<ChatMessage> window;
    private final Money openingOffer;
    private final Money maxOffer;
    private final Money previousOffer;

    /**
     * @param openingOffer the offer the buyer must open with; null after the first offer
     * @param previousOffer the buyer's last offer; null before the first one
     */
    public BuyerTurnTask(Persona persona, VehicleFacts facts, String listingText, List<ChatMessage> window,
                         Money openingOffer, Money maxOffer, Money previousOffer) {
        this.persona = persona;
        this.facts = facts;
        this.listingText = listingText;
        this.window = List.copyOf(window);
        this.openingOffer = openingOffer;
        this.maxOffer = maxOffer;
        this.previousOffer = previousOffer;
    }

    @Override
    public String name() {
        return "buyer-turn";
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.persona(persona, facts, listingText, window, openingOffer, maxOffer, previousOffer);
    }

    @Override
    public Class<BuyerTurn> resultType() {
        return BuyerTurn.class;
    }

    @Override
    public List<String> validate(BuyerTurn turn) {
        List<String> problems = new ArrayList<>();
        if (turn.message() == null || turn.message().isBlank() || turn.intent() == null) {
            return List.of("The JSON needs a message and an intent.");
        }
        if (turn.intent() == BuyerIntent.MAKE_OFFER && turn.offerAmount() == null) {
            problems.add("A MAKE_OFFER turn needs an offerAmount.");
        }
        if (turn.offerAmount() != null) {
            int offer = turn.offerAmount();
            if (openingOffer != null && Math.abs(offer - openingOffer.toDouble()) > 1) {
                problems.add("The first offer must be exactly " + openingOffer + ".");
            }
            if (previousOffer != null && offer < previousOffer.toDouble()) {
                problems.add("A buyer never lowers an offer; the last offer was " + previousOffer + ".");
            }
            if (offer > maxOffer.toDouble()) {
                problems.add("This buyer will not offer more than " + maxOffer + ".");
            }
        }
        Matcher km = KILOMETRES.matcher(turn.message());
        while (km.find()) {
            if (Integer.parseInt(km.group(1).replace(",", "")) != facts.odometerKm()) {
                problems.add("The odometer reads " + facts.odometerKm() + " km; do not state other readings.");
            }
        }
        Matcher year = MODEL_YEAR.matcher(turn.message());
        while (year.find()) {
            if (Integer.parseInt(year.group(1)) != facts.year()) {
                problems.add("The vehicle is a " + facts.year() + "; do not state other model years.");
            }
        }
        return problems;
    }

    /** Without the AI there is no buyer; the simulator reports that it cannot run. */
    @Override
    public BuyerTurn fallback() {
        return null;
    }
}
