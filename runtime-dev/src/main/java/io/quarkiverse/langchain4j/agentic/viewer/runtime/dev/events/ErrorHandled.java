package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * An agent failed and the error handler of its workflow decided what to do: retry it, use a result instead, or
 * rethrow (langchain4j's default when no {@code @ErrorHandler} is declared). Not in the spec event table: without it a
 * recovered error is invisible, as a missing argument fails before the agent is invoked.
 *
 * @param nodeId topology node of the failing agent, null when not found
 * @param invocationId invocation of the workflow running the failing agent
 * @param agentName name of the failing agent
 * @param error the error
 * @param outcome what the handler decided: {@code RETRY}, {@code RETURN_RESULT} or {@code THROW_EXCEPTION}
 * @param result the result used instead, for RETURN_RESULT
 */
public record ErrorHandled(String runId, long timestamp, String nodeId, String invocationId, String agentName,
        ErrorInfo error, String outcome, Object result) implements ExecutionEvent {
}
