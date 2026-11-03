package avsa.application;

import java.time.Instant;
import java.util.UUID;

/** A listing was generated or edited (F07). */
public final class ListingGenerated extends AgentEvent {
    public ListingGenerated(Instant occurredAt, UUID sessionId) {
        super(occurredAt, sessionId);
    }
}
