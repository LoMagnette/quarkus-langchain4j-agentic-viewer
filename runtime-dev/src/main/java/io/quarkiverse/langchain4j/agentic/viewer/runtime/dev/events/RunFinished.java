package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * A run ended with a result.
 *
 * @param output result of the root agent
 * @param durationMillis run duration
 */
public record RunFinished(String runId, long timestamp, Object output, long durationMillis) implements ExecutionEvent {
}
