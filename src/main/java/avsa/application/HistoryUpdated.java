package avsa.application;

import java.time.Instant;
import java.util.UUID;

/** A confirmed vehicle history was stored (F04). */
public final class HistoryUpdated extends AgentEvent {
    public HistoryUpdated(Instant occurredAt, UUID sessionId) {
        super(occurredAt, sessionId);
    }
}
