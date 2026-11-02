package avsa.agent;

import java.util.Objects;
import java.util.Set;

/**
 * The model's answer.
 *
 * @param sourceUrls pages the model's web searches returned or cited; empty without web search
 */
public record LLMResponse(String text, Set<String> sourceUrls, long inputTokens, long outputTokens, long webSearches,
                          String stopReason) {

    public LLMResponse {
        Objects.requireNonNull(text, "text");
        sourceUrls = Set.copyOf(sourceUrls);
        stopReason = stopReason == null ? "" : stopReason;
    }

    public static LLMResponse ofText(String text) {
        return new LLMResponse(text, Set.of(), 0, 0, 0, "end_turn");
    }
}
