package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

import java.util.Map;

/**
 * A {@code @HumanInTheLoop} step waits for an answer (provideHumanInput).
 *
 * @param nodeId topology node of the human-in-the-loop step
 * @param invocationId invocation of the step (its AgentStarted), null when unknown
 * @param prompt what is asked (the step's description), null when unknown
 * @param expectedKey scope key the answer is stored under
 * @param valueType Java type of the answer: a String is taken as typed, anything else as JSON
 * @param inputs the step's arguments by name, bounded like other payloads
 */
public record AwaitingHumanInput(String runId, long timestamp, String nodeId, String invocationId, String prompt,
        String expectedKey, String valueType, Map<String, Object> inputs) implements ExecutionEvent {
}
