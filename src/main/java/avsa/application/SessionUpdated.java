package avsa.application;

import java.time.Instant;
import java.util.UUID;

/** Another part of the session changed, such as the forecast or the offer log. */
public final class SessionUpdated extends AgentEvent {

    private final String part;

    public SessionUpdated(Instant occurredAt, UUID sessionId, String part) {
        super(occurredAt, sessionId);
        this.part = part;
    }

    public String part() {
        return part;
    }
}
