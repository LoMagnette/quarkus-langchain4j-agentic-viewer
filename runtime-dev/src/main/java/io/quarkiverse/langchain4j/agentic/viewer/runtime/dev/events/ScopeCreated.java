package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * An AgenticScope was created for the run.
 *
 * @param memoryId memory id of the scope
 */
public record ScopeCreated(String runId, long timestamp, String memoryId) implements ExecutionEvent {
}
