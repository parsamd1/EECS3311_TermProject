package avsa.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Something that changed in the current session; listeners such as autosave and the GUI react to it. */
public abstract class AgentEvent {

    private final Instant occurredAt;
    private final UUID sessionId;

    protected AgentEvent(Instant occurredAt, UUID sessionId) {
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.sessionId = Objects.requireNonNull(sessionId);
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public UUID sessionId() {
        return sessionId;
    }
}
