package avsa.agent;

/** Access to a language model. Adapter: the rest of the application never sees the vendor SDK. */
public interface LLMClient {

    /** @throws LLMUnavailableException the model could not answer */
    LLMResponse complete(LLMRequest request);
}
