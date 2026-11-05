package avsa.services;

import avsa.domain.ChatTurn;
import avsa.domain.Money;
import avsa.domain.Speaker;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows the numbers in a simulated negotiation (F09): the buyer's offers and the lowest price
 * the seller named, compared with the asking price and the walk-away floor.
 */
public final class NegotiationTracker {

    private static final Pattern DOLLARS = Pattern.compile("\\$\\s?(\\d[\\d,]*)");

    private final Money askingPrice;
    private final Money floor;
    private Money latestOffer;
    private Money lowestSellerPrice;

    public NegotiationTracker(Money askingPrice, Money floor) {
        this.askingPrice = askingPrice;
        this.floor = floor;
    }

    public void record(ChatTurn turn) {
        if (turn.speaker() == Speaker.BUYER) {
            turn.offer().ifPresent(o -> latestOffer = o);
            return;
        }
        Matcher m = DOLLARS.matcher(turn.message());
        while (m.find()) {
            Money named = Money.cad(Long.parseLong(m.group(1).replace(",", "")));
            // Small amounts, such as a repair cost, are not prices.
            if (named.toDouble() >= askingPrice.toDouble() * 0.5
                    && (lowestSellerPrice == null || named.compareTo(lowestSellerPrice) < 0)) {
                lowestSellerPrice = named;
            }
        }
    }

    public Optional<Money> latestOffer() {
        return Optional.ofNullable(latestOffer);
    }

    public Optional<Money> lowestSellerPrice() {
        return Optional.ofNullable(lowestSellerPrice);
    }

    public boolean isBelowFloor(Money amount) {
        return amount.compareTo(floor) < 0;
    }

    /** Whether the seller held the walk-away floor, in plain words. */
    public String floorFeedback() {
        if (lowestSellerPrice == null) {
            return "You did not name a price, so the floor of " + floor + " was never tested.";
        }
        return isBelowFloor(lowestSellerPrice)
                ? "You offered " + lowestSellerPrice + ", which is below your walk-away floor of " + floor
                        + ". A dealer would pay more than that."
                : "You held your floor: the lowest price you named was " + lowestSellerPrice + " (floor " + floor + ").";
    }
}
