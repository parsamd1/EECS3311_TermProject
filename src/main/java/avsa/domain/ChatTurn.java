package avsa.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One message in a simulated negotiation; buyer turns may carry an offer and an intent (F09). */
public record ChatTurn(Speaker speaker, String message, Money offerAmount, BuyerIntent intent, Instant timestamp) {

    public ChatTurn {
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public Optional<Money> offer() {
        return Optional.ofNullable(offerAmount);
    }
}
