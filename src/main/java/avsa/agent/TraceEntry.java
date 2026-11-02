package avsa.agent;

import java.time.Instant;

/** One step the agent took. {@code error} is null unless something failed. */
public record TraceEntry(Instant timestamp, TraceKind kind, String name, String payload, String error) {
}
