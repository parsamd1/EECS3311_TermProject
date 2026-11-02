package avsa.agent;

import avsa.infrastructure.JsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** A record of every prompt, response and failure, shown with the CLI's --json option. */
public final class AgentTrace {

    private final Clock clock;
    private final List<TraceEntry> entries = new ArrayList<>();

    public AgentTrace(Clock clock) {
        this.clock = clock;
    }

    public void recordPrompt(LLMRequest request) {
        add(TraceKind.PROMPT, request.purpose(), request.user(), null);
    }

    public void recordResponse(String purpose, LLMResponse response) {
        add(TraceKind.RESPONSE, purpose, response.text(), null);
    }

    public void recordRejected(String purpose, List<String> problems) {
        add(TraceKind.REJECTED, purpose, String.join("; ", problems), null);
    }

    public void recordError(String purpose, String error) {
        add(TraceKind.ERROR, purpose, "", error);
    }

    public void recordFallback(String purpose) {
        add(TraceKind.FALLBACK, purpose, "", null);
    }

    private void add(TraceKind kind, String name, String payload, String error) {
        entries.add(new TraceEntry(clock.instant(), kind, name, payload, error));
    }

    public List<TraceEntry> entries() {
        return List.copyOf(entries);
    }

    public String toJson(JsonMapper mapper) {
        try {
            return new String(mapper.toBytes(entries), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The trace could not be written as JSON", e);
        }
    }
}
