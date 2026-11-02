package avsa.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runs AI tasks with the standard failure handling (Section 2.2): call, parse, validate.
 * An unreachable model is retried with the same request; a malformed or invalid answer is
 * retried with a repair prompt. After the last attempt the task's fallback is used and labelled.
 */
public final class AiTaskRunner {

    static final int MAX_ATTEMPTS = 2;

    private final PromptComposer composer;
    private final LLMClient client;
    private final ResponseParser parser;
    private final AgentTrace trace;
    private final int maxAttempts;

    public AiTaskRunner(PromptComposer composer, LLMClient client, ResponseParser parser, AgentTrace trace) {
        this(composer, client, parser, trace, MAX_ATTEMPTS);
    }

    AiTaskRunner(PromptComposer composer, LLMClient client, ResponseParser parser, AgentTrace trace, int maxAttempts) {
        this.composer = composer;
        this.client = client;
        this.parser = parser;
        this.trace = trace;
        this.maxAttempts = maxAttempts;
    }

    public PromptComposer composer() {
        return composer;
    }

    public <T> AiResult<T> run(AiTask<T> task) {
        LLMRequest request = task.buildPrompt(composer);
        List<String> notes = new ArrayList<>();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            trace.recordPrompt(request);
            LLMResponse response;
            try {
                response = client.complete(request);
            } catch (LLMUnavailableException e) {
                trace.recordError(task.name(), e.getMessage());
                notes.add("Attempt " + attempt + ": " + e.getMessage());
                continue;
            }
            trace.recordResponse(task.name(), response);
            ParseResult<T> parsed = parser.parse(response, task.resultType());
            List<String> problems = parsed.parsed() ? task.validate(parsed.value(), response) : List.of(parsed.error());
            if (problems.isEmpty()) {
                return new AiResult<>(parsed.value(), false, notes, response.sourceUrls());
            }
            trace.recordRejected(task.name(), problems);
            notes.add("Attempt " + attempt + " was rejected: " + String.join("; ", problems));
            request = composer.repair(request, problems);
        }
        trace.recordFallback(task.name());
        notes.add("The AI result could not be used, so a fallback was used.");
        return new AiResult<>(task.fallback(), true, notes, Set.of());
    }
}
