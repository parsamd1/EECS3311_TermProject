package avsa.application;

import java.time.Instant;
import java.util.UUID;

/** A new market snapshot was stored (F02). */
public final class MarketDataReady extends AgentEvent {
    public MarketDataReady(Instant occurredAt, UUID sessionId) {
        super(occurredAt, sessionId);
    }
}
