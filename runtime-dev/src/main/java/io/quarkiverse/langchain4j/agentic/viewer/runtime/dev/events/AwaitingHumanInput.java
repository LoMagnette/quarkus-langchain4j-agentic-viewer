package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * A {@code @HumanInTheLoop} step waits for an answer (provideHumanInput).
 *
 * @param nodeId topology node of the human-in-the-loop step
 * @param prompt what is asked, null when unknown
 * @param expectedKey scope key the answer is stored under
 */
public record AwaitingHumanInput(String runId, long timestamp, String nodeId, String prompt, String expectedKey)
        implements ExecutionEvent {
}
