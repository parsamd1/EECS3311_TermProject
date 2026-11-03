package avsa.application;

import java.time.Instant;
import java.util.UUID;

/** The vehicle profile was saved (F01). */
public final class ProfileUpdated extends AgentEvent {
    public ProfileUpdated(Instant occurredAt, UUID sessionId) {
        super(occurredAt, sessionId);
    }
}
