package avsa.application;

import java.time.Instant;
import java.util.UUID;

/** A price was accepted as the active price (F03). */
public final class PriceRecommended extends AgentEvent {
    public PriceRecommended(Instant occurredAt, UUID sessionId) {
        super(occurredAt, sessionId);
    }
}
