package avsa.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.domain.BuyerIntent;
import avsa.domain.ChatTurn;
import avsa.domain.Money;
import avsa.domain.Speaker;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NegotiationTrackerTest {

    private static Optional<Money> lowestNamedIn(String... sellerMessages) {
        NegotiationTracker tracker = new NegotiationTracker(Money.cad(24_100), Money.cad(22_900));
        for (String text : sellerMessages) {
            tracker.record(new ChatTurn(Speaker.SELLER, text, null, BuyerIntent.CHAT, Instant.EPOCH));
        }
        return tracker.lowestSellerPrice();
    }

    @Test
    void pricesAreFoundWithOrWithoutADollarSign() {
        assertEquals(Optional.of(Money.cad(24_000)), lowestNamedIn("The price is 24,100.",
                "I can do 24,000 if you pick it up this week."));
        assertEquals(Optional.of(Money.cad(23_500)), lowestNamedIn("How about $23,500?"));
        assertEquals(Optional.of(Money.cad(23_800)), lowestNamedIn("23800 is my best price"));
        assertEquals(Optional.of(Money.cad(23_500)), lowestNamedIn("I could go to 23.5k"));
    }

    @Test
    void distancesYearsAndRepairCostsAreNotPrices() {
        assertTrue(lowestNamedIn("It has 49,000 km and it is a 2022.", "The scratch would cost $300 to fix.",
                "It did 120000 kms on the highway.").isEmpty());
    }

    @Test
    void namingAPriceBelowTheFloorIsReported() {
        NegotiationTracker tracker = new NegotiationTracker(Money.cad(24_100), Money.cad(22_900));
        tracker.record(new ChatTurn(Speaker.SELLER, "Fine, 22,000 and it's yours.", null, BuyerIntent.CHAT,
                Instant.EPOCH));
        assertTrue(tracker.floorFeedback().contains("below your walk-away floor"));
    }
}
