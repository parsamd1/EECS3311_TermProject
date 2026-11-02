package avsa.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.infrastructure.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Calls the real Claude API, so it costs a little money and needs a key in .env.
 * Runs only when AVSA_LIVE_TESTS=true.
 */
@EnabledIfEnvironmentVariable(named = "AVSA_LIVE_TESTS", matches = "true")
class ClaudeClientLiveTest {

    @Test
    void answersAPlainRequest() {
        ClaudeClient client = new ClaudeClient(AppConfig.load());
        LLMResponse response = client.complete(new LLMRequest("live-check", "Reply with one word.",
                "What colour is a clear daytime sky?", 1_000, null));
        System.out.println("LIVE text=" + response.text() + " in=" + response.inputTokens() + " out="
                + response.outputTokens() + " stop=" + response.stopReason());
        assertFalse(response.text().isBlank());
        assertTrue(response.text().toLowerCase().contains("blue"));
    }
}
