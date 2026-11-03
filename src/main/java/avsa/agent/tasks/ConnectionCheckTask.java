package avsa.agent.tasks;

import avsa.agent.AiTask;
import avsa.agent.LLMRequest;
import avsa.agent.PromptComposer;
import java.util.List;

/** The smallest possible request, to check that the key, credit and connection work. */
public final class ConnectionCheckTask implements AiTask<String> {

    @Override
    public String name() {
        return "connection-check";
    }

    @Override
    public LLMRequest buildPrompt(PromptComposer composer) {
        return composer.connectionCheck();
    }

    @Override
    public Class<String> resultType() {
        return String.class;
    }

    @Override
    public List<String> validate(String result) {
        return result.toUpperCase().contains("OK") ? List.of() : List.of("Reply with the word OK.");
    }

    @Override
    public String fallback() {
        return "";
    }
}
