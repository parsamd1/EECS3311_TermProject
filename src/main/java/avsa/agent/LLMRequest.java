package avsa.agent;

import java.util.Objects;
import java.util.Optional;

/**
 * One request to the language model. Built only by {@link PromptComposer}.
 *
 * @param webSearch null for a request that may not search the web
 */
public record LLMRequest(String purpose, String system, String user, long maxTokens, WebSearchOptions webSearch) {

    public static final long DEFAULT_MAX_TOKENS = 16_000;

    public LLMRequest {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(user, "user");
    }

    public Optional<WebSearchOptions> webSearchOptions() {
        return Optional.ofNullable(webSearch);
    }

    public LLMRequest withUser(String newUser) {
        return new LLMRequest(purpose, system, newUser, maxTokens, webSearch);
    }
}
