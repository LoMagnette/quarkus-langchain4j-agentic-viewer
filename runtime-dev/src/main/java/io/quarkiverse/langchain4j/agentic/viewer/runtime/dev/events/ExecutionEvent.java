package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

/**
 * An event of a run, streamed to the Dev UI (spec "Execution event model"). Agent events follow AgentMonitor semantics
 * (decision-4): an invocation id per agent call, the parent invocation for correlation, the loop iteration index and
 * the token usage.
 */
public sealed interface ExecutionEvent permits RunStarted, RunFinished, RunFailed, ScopeCreated, ScopeDestroyed,
        AgentStarted, AgentFinished, AgentFailed, ToolStarted, ToolFinished, PlannerDecision, ScopeStateChanged,
        AwaitingHumanInput {

    /** @return the run the event belongs to */
    String runId();

    /** @return when the event happened, in milliseconds since the epoch */
    long timestamp();

    /**
     * @return the event type, e.g. {@code AgentStarted}: serialized as {@code type} so the Dev UI can tell events apart
     */
    default String getType() {
        return getClass().getSimpleName();
    }
}
