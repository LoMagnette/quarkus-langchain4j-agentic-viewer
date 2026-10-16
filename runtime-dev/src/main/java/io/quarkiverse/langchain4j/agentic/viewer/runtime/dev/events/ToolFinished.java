package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * A tool call returned.
 *
 * @param invocationId invocation of the calling agent
 * @param toolCallId id of the tool call, as in ToolStarted
 * @param toolName tool name
 * @param result result text given back to the model
 * @param durationMillis call duration
 */
public record ToolFinished(String runId, long timestamp, String invocationId, String toolCallId, String toolName,
        String result, long durationMillis) implements ExecutionEvent {
}
