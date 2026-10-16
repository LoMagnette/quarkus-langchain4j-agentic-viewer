package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * A key of the AgenticScope got a new value (derived from agent outputs).
 *
 * @param key scope key
 * @param value new value, possibly truncated
 * @param truncated whether the value was truncated
 * @param nodeId node of the agent that wrote it, null when unknown
 */
public record ScopeStateChanged(String runId, long timestamp, String key, Object value, boolean truncated, String nodeId)
        implements ExecutionEvent {
}
