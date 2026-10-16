package io.quarkiverse.langchain4j.agentic.viewer.test.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeCreated;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeDestroyed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeStateChanged;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.TokenUsage;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolStarted;

/**
 * Every execution event of the spec table serializes to JSON with its run id, timestamp and type.
 */
class ExecutionEventJsonTest {

    private static final String RUN = "run-1";
    private static final long T = 1_760_000_000_000L;
    private static final String NODE = "com.example.Story#write/Writer";
    private static final ErrorInfo ERROR = ErrorInfo.of(new IllegalStateException("boom"));

    private static final List<ExecutionEvent> ONE_OF_EACH = List.of(
            new RunStarted(RUN, T, "com.example.Story#write", Map.of("topic", "dragons")),
            new RunFinished(RUN, T, "a story", 1200),
            new RunFailed(RUN, T, ERROR, 30),
            new ScopeCreated(RUN, T, "memory-1"),
            new ScopeDestroyed(RUN, T, "memory-1"),
            new AgentStarted(RUN, T, NODE, "inv-2", "inv-1", "write", 0, null, Map.of("topic", "dragons"), false),
            new AgentFinished(RUN, T, NODE, "inv-2", "a draft", new TokenUsage(12, 34, 46), 800),
            new AgentFailed(RUN, T, NODE, "inv-2", ERROR, 15),
            new ToolStarted(RUN, T, "inv-2", "call-1", "credit", "{\"amount\":10}"),
            new ToolFinished(RUN, T, "inv-2", "call-1", "credit", "110.0", 5),
            new PlannerDecision(RUN, T, "com.example.Bank#run", "inv-1", List.of(NODE), false,
                    Map.of("path", List.of("a", "b"))),
            new ScopeStateChanged(RUN, T, "draft", "a draft", false, NODE),
            new AwaitingHumanInput(RUN, T, "com.example.Trip#plan/Approval", "Approve?", "approval"));

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everySpecEventExists() {
        Set<String> spec = Set.of("RunStarted", "RunFinished", "RunFailed", "ScopeCreated", "ScopeDestroyed",
                "AgentStarted", "AgentFinished", "AgentFailed", "ToolStarted", "ToolFinished", "PlannerDecision",
                "ScopeStateChanged", "AwaitingHumanInput");
        assertEquals(spec, Arrays.stream(ExecutionEvent.class.getPermittedSubclasses()).map(Class::getSimpleName)
                .collect(Collectors.toSet()), "the sealed hierarchy is the spec table");
        assertEquals(spec, ONE_OF_EACH.stream().map(ExecutionEvent::getType).collect(Collectors.toSet()));
    }

    @Test
    void everyEventSerializesWithRunIdTimestampAndType() throws Exception {
        for (ExecutionEvent event : ONE_OF_EACH) {
            JsonNode json = mapper.readTree(mapper.writeValueAsString(event));
            assertEquals(RUN, json.get("runId").asText(), json::toString);
            assertEquals(T, json.get("timestamp").asLong(), json::toString);
            assertEquals(event.getClass().getSimpleName(), json.get("type").asText(), json::toString);
        }
    }

    @Test
    void agentEventsFollowAgentMonitorSemantics() throws Exception {
        JsonNode started = mapper.readTree(mapper.writeValueAsString(ONE_OF_EACH.get(5)));
        assertEquals("{\"runId\":\"run-1\",\"timestamp\":1760000000000,\"nodeId\":\"" + NODE + "\",\"invocationId\":\"inv-2\","
                + "\"parentInvocationId\":\"inv-1\",\"agentName\":\"write\",\"iteration\":0,\"itemIndex\":null,\"inputs\":{\"topic\":\"dragons\"},"
                + "\"streaming\":false,\"type\":\"AgentStarted\"}", started.toString());
        JsonNode finished = mapper.readTree(mapper.writeValueAsString(ONE_OF_EACH.get(6)));
        assertEquals("{\"inputTokens\":12,\"outputTokens\":34,\"totalTokens\":46}", finished.get("tokens").toString());
    }

    @Test
    void errorsCarryTheExceptionAndItsTrace() throws Exception {
        JsonNode error = mapper.readTree(mapper.writeValueAsString(ONE_OF_EACH.get(2))).get("error");
        assertEquals(IllegalStateException.class.getName(), error.get("exceptionClass").asText());
        assertEquals("boom", error.get("message").asText());
        assertTrue(error.get("stackTrace").asText().contains("ExecutionEventJsonTest"));
    }
}
