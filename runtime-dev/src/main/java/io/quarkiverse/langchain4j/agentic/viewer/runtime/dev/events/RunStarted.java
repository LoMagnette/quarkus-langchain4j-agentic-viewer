package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

import java.util.Map;

/**
 * A run of an agentic system started.
 *
 * @param systemId id of the system
 * @param inputs input values by name
 */
public record RunStarted(String runId, long timestamp, String systemId, Map<String, Object> inputs)
        implements ExecutionEvent {
}
