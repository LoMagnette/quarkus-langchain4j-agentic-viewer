package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * An agent invocation threw.
 *
 * @param nodeId topology node
 * @param invocationId invocation, as in AgentStarted
 * @param error the error
 * @param durationMillis invocation duration
 */
public record AgentFailed(String runId, long timestamp, String nodeId, String invocationId, ErrorInfo error,
        long durationMillis) implements ExecutionEvent {
}
