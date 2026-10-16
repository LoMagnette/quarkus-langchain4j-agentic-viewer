package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * An agent invocation returned.
 *
 * @param nodeId topology node
 * @param invocationId invocation, as in AgentStarted
 * @param output returned value
 * @param tokens token usage of an AI agent, null otherwise or when not reported
 * @param durationMillis invocation duration
 */
public record AgentFinished(String runId, long timestamp, String nodeId, String invocationId, Object output,
        TokenUsage tokens, long durationMillis) implements ExecutionEvent {
}
