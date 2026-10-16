package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * The AgenticScope of the run is about to be destroyed.
 *
 * @param memoryId memory id of the scope
 */
public record ScopeDestroyed(String runId, long timestamp, String memoryId) implements ExecutionEvent {
}
