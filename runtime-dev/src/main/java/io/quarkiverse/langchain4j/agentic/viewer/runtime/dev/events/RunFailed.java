package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * A run ended with an error (cancellation included).
 *
 * @param error the error
 * @param durationMillis run duration
 */
public record RunFailed(String runId, long timestamp, ErrorInfo error, long durationMillis) implements ExecutionEvent {
}
