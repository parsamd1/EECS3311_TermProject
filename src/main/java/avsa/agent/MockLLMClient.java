package avsa.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * A scripted language model for tests and demonstrations without an API key. Each call
 * returns the next scripted response, or fails the way it was told to.
 */
public final class MockLLMClient implements LLMClient {

    public enum FailureMode {
        NONE,
        UNAVAILABLE
    }

    private final Queue<Object> scripted = new ArrayDeque<>();
    private final List<LLMRequest> received = new ArrayList<>();
    private FailureMode failureMode = FailureMode.NONE;
    private String failureReason = "Scripted failure";

    public MockLLMClient thenRespond(String text) {
        scripted.add(LLMResponse.ofText(text));
        return this;
    }

    public MockLLMClient thenRespond(LLMResponse response) {
        scripted.add(response);
        return this;
    }

    public MockLLMClient thenFail() {
        scripted.add(FailureMode.UNAVAILABLE);
        return this;
    }

    /** Every call fails until this is set back to {@link FailureMode#NONE}. */
    public MockLLMClient alwaysFail(FailureMode mode) {
        this.failureMode = mode;
        return this;
    }

    /** As {@link #alwaysFail(FailureMode)}, with the reason the user sees in the fallback notes. */
    public MockLLMClient alwaysFail(FailureMode mode, String reason) {
        this.failureReason = reason;
        return alwaysFail(mode);
    }

    public List<LLMRequest> received() {
        return List.copyOf(received);
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        received.add(request);
        Object next = failureMode == FailureMode.UNAVAILABLE ? FailureMode.UNAVAILABLE : scripted.poll();
        if (next == null || next == FailureMode.UNAVAILABLE) {
            throw new LLMUnavailableException(failureReason, null);
        }
        return (LLMResponse) next;
    }
}
