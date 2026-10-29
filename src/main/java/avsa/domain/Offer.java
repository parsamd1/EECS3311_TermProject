package avsa.domain;

import java.time.Instant;
import java.util.Objects;

/** An offer the seller received. {@code notes} may include the buyer's stated reasons. */
public record Offer(Money amount, BuyerType buyerType, String notes, Instant receivedAt) {

    public Offer {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(buyerType, "buyerType");
        Objects.requireNonNull(receivedAt, "receivedAt");
        notes = notes == null ? "" : notes.strip();
    }
}
