package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

import java.util.Map;

/**
 * An agent (or workflow) invocation started.
 *
 * @param nodeId topology node the invocation belongs to
 * @param invocationId id of this invocation
 * @param parentInvocationId invocation of the parent workflow, null for the root
 * @param agentName agent name
 * @param iteration loop iteration index (from 0) when inside a loop, null otherwise
 * @param inputs input values by scope key
 * @param streaming whether the agent streams its response: its output comes with AgentFinished when the stream
 *        completes (decision-5)
 */
public record AgentStarted(String runId, long timestamp, String nodeId, String invocationId, String parentInvocationId,
        String agentName, Integer iteration, Map<String, Object> inputs, boolean streaming) implements ExecutionEvent {
}
