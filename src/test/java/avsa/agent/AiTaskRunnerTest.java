package avsa.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avsa.infrastructure.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiTaskRunnerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);

    /** A small task: the model must return a positive count. */
    record Count(int count) {
    }

    private static final class CountTask implements AiTask<Count> {
        @Override
        public String name() {
            return "count";
        }

        @Override
        public LLMRequest buildPrompt(PromptComposer composer) {
            return new LLMRequest("count", "Answer in JSON.", "How many wheels does a car have?",
                    LLMRequest.DEFAULT_MAX_TOKENS, null);
        }

        @Override
        public Class<Count> resultType() {
            return Count.class;
        }

        @Override
        public List<String> validate(Count result) {
            return result.count() > 0 ? List.of() : List.of("The count must be positive.");
        }

        @Override
        public Count fallback() {
            return new Count(-1);
        }
    }

    private final MockLLMClient llm = new MockLLMClient();
    private final AgentTrace trace = new AgentTrace(CLOCK);
    private final AiTaskRunner runner =
            new AiTaskRunner(new PromptComposer(), llm, new ResponseParser(new JsonMapper()), trace);

    private static List<TraceKind> kinds(AgentTrace trace) {
        return trace.entries().stream().map(TraceEntry::kind).toList();
    }

    @Test
    void aValidAnswerIsUsed() {
        llm.thenRespond("Here you go: ```json\n{\"count\": 4}\n```");
        AiResult<Count> result = runner.run(new CountTask());
        assertEquals(4, result.value().count());
        assertFalse(result.usedFallback());
        assertEquals(List.of(TraceKind.PROMPT, TraceKind.RESPONSE), kinds(trace));
    }

    @Test
    void aMalformedAnswerIsRepairedOnce() {
        llm.thenRespond("four").thenRespond("{\"count\": 4}");
        AiResult<Count> result = runner.run(new CountTask());
        assertEquals(4, result.value().count());
        assertFalse(result.usedFallback());
        String repaired = llm.received().get(1).user();
        assertTrue(repaired.contains("rejected"));
        assertTrue(repaired.contains("did not contain a JSON object"));
        assertEquals(1, result.notes().size());
    }

    @Test
    void anInvalidAnswerGetsTheValidationErrorsAsFeedback() {
        llm.thenRespond("{\"count\": 0}").thenRespond("{\"count\": 4}");
        assertEquals(4, runner.run(new CountTask()).value().count());
        assertTrue(llm.received().get(1).user().contains("The count must be positive."));
    }

    @Test
    void anUnreachableModelIsRetriedWithTheSameRequest() {
        llm.thenFail().thenRespond("{\"count\": 4}");
        AiResult<Count> result = runner.run(new CountTask());
        assertEquals(4, result.value().count());
        assertEquals(llm.received().get(0), llm.received().get(1));
        assertEquals(List.of(TraceKind.PROMPT, TraceKind.ERROR, TraceKind.PROMPT, TraceKind.RESPONSE), kinds(trace));
    }

    @Test
    void afterTwoFailedAttemptsTheFallbackIsUsedAndLabelled() {
        llm.thenRespond("{\"count\": 0}").thenRespond("{\"count\": 0}");
        AiResult<Count> result = runner.run(new CountTask());
        assertTrue(result.usedFallback());
        assertEquals(-1, result.value().count());
        assertEquals(2, llm.received().size());
        assertTrue(result.notes().get(result.notes().size() - 1).contains("fallback"));
        assertEquals(TraceKind.FALLBACK, kinds(trace).get(kinds(trace).size() - 1));
    }

    @Test
    void anUnavailableModelFallsBack() {
        llm.alwaysFail(MockLLMClient.FailureMode.UNAVAILABLE);
        AiResult<Count> result = runner.run(new CountTask());
        assertTrue(result.usedFallback());
        assertTrue(result.sources().isEmpty());
    }

    @Test
    void sourcesComeFromTheAcceptedResponse() {
        llm.thenRespond(new LLMResponse("{\"count\": 4}", Set.of("https://example.test/a"), 10, 5, 1, "end_turn"));
        assertEquals(Set.of("https://example.test/a"), runner.run(new CountTask()).sources());
    }

    @Test
    void traceCanBeWrittenAsJson() {
        llm.thenRespond("{\"count\": 4}");
        runner.run(new CountTask());
        String json = trace.toJson(new JsonMapper());
        assertTrue(json.contains("\"kind\" : \"PROMPT\""));
        assertTrue(json.contains("How many wheels"));
    }

    @Test
    void plainTextResultsAreAccepted() {
        ResponseParser parser = new ResponseParser(new JsonMapper());
        assertEquals("Hello", parser.parse(LLMResponse.ofText(" Hello "), String.class).value());
        assertFalse(parser.parse(LLMResponse.ofText(" "), String.class).parsed());
        assertFalse(parser.parse(LLMResponse.ofText("{\"count\": \"many\"}"), Count.class).parsed());
    }

    @Test
    void theSameUnavailableReasonIsNotedOnce() {
        llm.alwaysFail(MockLLMClient.FailureMode.UNAVAILABLE, "No API key is set.");
        AiResult<Count> result = runner.run(new CountTask());
        assertTrue(result.usedFallback());
        assertEquals(1, result.notes().stream().filter(n -> n.contains("No API key is set.")).count());
    }
}
