package avsa.agent;

import java.util.List;

/** One thing the AI is asked to do, with a deterministic fallback when it cannot (Section 2.2). */
public interface AiTask<T> {

    String name();

    LLMRequest buildPrompt(PromptComposer composer);

    Class<T> resultType();

    /** Problems with a parsed result; empty when it is acceptable. */
    List<String> validate(T result);

    /** Like {@link #validate(Object)}, for tasks that also check the response, such as its sources. */
    default List<String> validate(T result, LLMResponse response) {
        return validate(result);
    }

    T fallback();

    /** How many times the model is asked before the fallback is used; zero means the runner's default. */
    default int maxAttempts() {
        return 0;
    }
}
