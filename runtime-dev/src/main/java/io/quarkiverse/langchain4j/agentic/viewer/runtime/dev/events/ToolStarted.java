package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * A tool call started.
 *
 * @param invocationId invocation of the calling agent
 * @param toolCallId id of the tool call (from the model), null when none
 * @param toolName tool name
 * @param arguments arguments as sent by the model (JSON text)
 */
public record ToolStarted(String runId, long timestamp, String invocationId, String toolCallId, String toolName,
        String arguments) implements ExecutionEvent {
}
