package avsa.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.agent.AgentTrace;
import avsa.agent.AiTaskRunner;
import avsa.agent.ChatMessage;
import avsa.agent.MemoryManager;
import avsa.agent.MockLLMClient;
import avsa.agent.PromptComposer;
import avsa.agent.ResponseParser;
import avsa.domain.BodyStyle;
import avsa.domain.ChatTurn;
import avsa.domain.CoachingReport;
import avsa.domain.ConditionReport;
import avsa.domain.Defect;
import avsa.domain.DefectType;
import avsa.domain.FuelType;
import avsa.domain.GeneratedListing;
import avsa.domain.Location;
import avsa.domain.Money;
import avsa.domain.Persona;
import avsa.domain.Severity;
import avsa.domain.SimulationSession;
import avsa.domain.SimulationStatus;
import avsa.domain.Tone;
import avsa.domain.Transmission;
import avsa.domain.VehicleFacts;
import avsa.domain.VehicleProfile;
import avsa.infrastructure.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuyerSimulatorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private static final Money ASKING = Money.cad(30_000);
    private static final Money FLOOR = Money.cad(24_000);
    private static final VehicleFacts FACTS = VehicleFacts.from(VehicleProfile.builder().make("Mazda").model("CX-5")
            .year(2022).trim("GS").bodyStyle(BodyStyle.SUV).odometerKm(49_000).transmission(Transmission.AUTOMATIC)
            .fuelType(FuelType.GASOLINE).location(new Location("Toronto", "ON", 43.65, -79.38))
            .condition(new ConditionReport(null, List.of(new Defect(DefectType.SCRATCH, "rear door", Severity.MINOR, "",
                    null)))).build(), null, ASKING);
    private static final GeneratedListing LISTING = new GeneratedListing("2022 Mazda CX-5 GS", "Clean SUV, 49,000 km.",
            List.of(), List.of(), "Minor scratch on the rear door.", ASKING, List.of(), Tone.PROFESSIONAL, false, false,
            false, null);

    private final MockLLMClient llm = new MockLLMClient();
    private final AiTaskRunner ai = new AiTaskRunner(new PromptComposer(), llm, new ResponseParser(new JsonMapper()),
            new AgentTrace(CLOCK));
    private final BuyerSimulator simulator = new BuyerSimulator(ai, new MemoryManager(), CLOCK);

    private static String turn(String message, Integer offer, String intent, String gap) {
        return "{\"message\": \"" + message + "\", \"offerAmount\": " + offer + ", \"intent\": \"" + intent
                + "\", \"listingGap\": " + (gap == null ? "null" : "\"" + gap + "\"") + "}";
    }

    @Test
    void aLowBallerOpensAtTheCodeSetOfferAndCannotLowerIt() {
        llm.thenRespond(turn("Would you take $21,000?", 21_000, "MAKE_OFFER", "Are there service records?"));
        SimulationSession s = simulator.start(Persona.LOW_BALLER, LISTING, FACTS, FLOOR);
        assertEquals(Money.cad(21_000), s.latestBuyerOffer().orElseThrow(), "70% of $30,000");
        assertEquals(List.of("Are there service records?"), s.listingGaps());

        llm.thenRespond(turn("Fine, $20,000.", 20_000, "MAKE_OFFER", null))
                .thenRespond(turn("Okay, $22,000 then.", 22_000, "MAKE_OFFER", null));
        ChatTurn buyer = simulator.reply(s, FACTS, "I can do $29,000.");
        assertEquals(Money.cad(22_000), buyer.offer().orElseThrow());
        assertTrue(llm.received().get(2).user().contains("never lowers an offer"));
        assertEquals(3, s.transcript().size(), "buyer, seller, buyer");
    }

    @Test
    void aDealerNeverOffersAboveTheFloorAndTheBuyerCannotStateWrongFacts() {
        llm.thenRespond(turn("$23,400 cash today.", 23_400, "MAKE_OFFER", null));
        SimulationSession s = simulator.start(Persona.DEALER, LISTING, FACTS, FLOOR);
        llm.thenRespond(turn("$26,000, final.", 26_000, "MAKE_OFFER", null))
                .thenRespond(turn("With 80,000 km I can only do $24,000.", 24_000, "MAKE_OFFER", null))
                .thenRespond(turn("$24,000, final.", 24_000, "MAKE_OFFER", null));
        assertThrows(IllegalStateException.class, () -> simulator.reply(s, FACTS, "Can you do better?"),
                "two invalid answers in a row: the turn fails rather than inventing facts");
        assertTrue(llm.received().get(2).user().contains("will not offer more than $24000"));
    }

    @Test
    void acceptingOrWalkingAwayEndsTheNegotiation() {
        llm.thenRespond(turn("Hi, is it available?", null, "ASK_QUESTION", null));
        SimulationSession s = simulator.start(Persona.SERIOUS, LISTING, FACTS, FLOOR);
        llm.thenRespond(turn("Deal at $28,500.", 28_500, "ACCEPT", null));
        simulator.reply(s, FACTS, "Yes. I could do $28,500.");
        assertEquals(SimulationStatus.AGREED, s.status());
        assertThrows(IllegalStateException.class, () -> simulator.reply(s, FACTS, "Hello?"));
    }

    @Test
    void coachingChecksTheFloorWithCodeAndDisclosureWithTheAi() {
        llm.thenRespond(turn("Hi, any damage?", null, "ASK_QUESTION", null));
        SimulationSession s = simulator.start(Persona.SUSPICIOUS, LISTING, FACTS, FLOOR);
        llm.thenRespond(turn("I'll send $33,000, refund the rest.", 31_500, "MAKE_OFFER", null));
        simulator.reply(s, FACTS, "No damage. I'd take $23,000.");
        llm.thenRespond("{\"disclosureFeedback\": \"You hid the scratch.\", \"redFlags\": [\"Overpayment offer\"]}");
        CoachingReport r = simulator.end(s, FACTS);
        assertTrue(r.aiWritten());
        assertTrue(r.floorFeedback().contains("$23000, which is below your walk-away floor"));
        assertEquals(List.of("Overpayment offer"), r.redFlags());
        assertEquals(SimulationStatus.ENDED, s.status());
    }

    @Test
    void withoutTheAiTheSimulatorSaysItCannotRunButCoachingStillWorks() {
        llm.alwaysFail(MockLLMClient.FailureMode.UNAVAILABLE);
        assertThrows(IllegalStateException.class, () -> simulator.start(Persona.SERIOUS, LISTING, FACTS, FLOOR));
        SimulationSession s = new SimulationSession(UUID.randomUUID(), Persona.SERIOUS, 10, ASKING, FLOOR, LISTING);
        s.add(new ChatTurn(avsa.domain.Speaker.SELLER, "There is a small scratch.", null, null, CLOCK.instant()));
        CoachingReport r = simulator.end(s, FACTS);
        assertFalse(r.aiWritten());
        assertEquals("You mentioned the scratch.", r.disclosureFeedback());
        assertTrue(r.floorFeedback().contains("never tested"));
    }

    @Test
    void memoryKeepsRecentTurnsAndSummarizesOlderOnes() {
        MemoryManager memory = new MemoryManager(3);
        UUID id = UUID.randomUUID();
        for (int i = 1; i <= 5; i++) {
            memory.append(id, new ChatMessage("buyer", "message " + i));
        }
        List<ChatMessage> window = memory.window(id);
        assertEquals(4, window.size());
        assertEquals("Earlier in the conversation: buyer: message 1 | buyer: message 2", window.get(0).text());
        assertEquals("message 5", window.get(3).text());
    }

    @Test
    void nearDuplicateListingGapsAreNotedOnce() {
        SimulationSession s = new SimulationSession(UUID.randomUUID(), Persona.SERIOUS, 10, ASKING, FLOOR, LISTING);
        s.addListingGap("Service records and confirmation of no prior accidents are not stated in the listing.");
        s.addListingGap("The listing does not state whether service records exist or confirm there were no accidents.");
        s.addListingGap("Are the winter tires on rims?");
        assertEquals(2, s.listingGaps().size());
    }

    @Test
    void personaLimitsAreSetByCode() {
        SimulationSession low = new SimulationSession(UUID.randomUUID(), Persona.LOW_BALLER, 10, ASKING, FLOOR, LISTING);
        assertEquals(Money.cad(27_000), BuyerSimulator.maxOffer(low), "halfway between floor and asking");
        SimulationSession dealer = new SimulationSession(UUID.randomUUID(), Persona.DEALER, 10, ASKING, FLOOR, LISTING);
        assertEquals(FLOOR, BuyerSimulator.maxOffer(dealer));
        assertEquals(Money.cad(23_400), BuyerSimulator.openingOffer(dealer));
    }
}
