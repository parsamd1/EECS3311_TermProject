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

    /** "$24,100", "24,100" or "24100"; not a distance such as "49,000 km". */
    private static final Pattern AMOUNT = Pattern.compile(
            "\\$?\\s?(\\d{1,3}(?:,\\d{3})+|\\d{4,7})(?![\\d,])(?!\\s*(?:km|kms|kilomet|mi\\b|miles))",
            Pattern.CASE_INSENSITIVE);
    /** "24k" or "24.5k". */
    private static final Pattern THOUSANDS = Pattern.compile("\\$?\\s?(\\d{1,3}(?:\\.\\d)?)\\s?k\\b",
            Pattern.CASE_INSENSITIVE);

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
        Matcher m = AMOUNT.matcher(turn.message());
        while (m.find()) {
            consider(Money.cad(Long.parseLong(m.group(1).replace(",", ""))));
        }
        Matcher k = THOUSANDS.matcher(turn.message());
        while (k.find()) {
            consider(Money.cad(Math.round(Double.parseDouble(k.group(1)) * 1000)));
        }
    }

    /** Small amounts, such as a repair cost or a year, and amounts far above asking are not prices. */
    private void consider(Money named) {
        double value = named.toDouble();
        if (value >= askingPrice.toDouble() * 0.5 && value <= askingPrice.toDouble() * 1.5
                && (lowestSellerPrice == null || named.compareTo(lowestSellerPrice) < 0)) {
            lowestSellerPrice = named;
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
