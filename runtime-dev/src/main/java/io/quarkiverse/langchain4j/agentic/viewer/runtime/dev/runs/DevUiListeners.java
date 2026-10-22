package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * The Dev UI listeners of one root agent: the hooks (the inherited listener, the human-in-the-loop steps, the planner
 * decorator) are installed once per root and serve every Dev UI listener attached to it, each handling the runs of its
 * own manager. A second attach (tests, several managers) adds a listener instead of stacking hooks; a listener whose
 * manager was shut down leaves the group. It knows the system of its root: a call of the root outside a Dev UI run is an
 * application run of that system.
 */
final class DevUiListeners implements AgentListener {

    private final String systemId;
    private final List<DevUiAgentListener> listeners = new CopyOnWriteArrayList<>();

    DevUiListeners(String systemId) {
        this.systemId = systemId;
    }

    void add(DevUiAgentListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** The listeners still in use (their manager not shut down). */
    List<DevUiAgentListener> listeners() {
        listeners.removeIf(DevUiAgentListener::isClosed);
        return listeners;
    }

    /**
     * @return the listener whose manager records the run the scope belongs to, the one of a Dev UI run first; null for
     *         an invocation no manager records
     */
    DevUiAgentListener owner(AgenticScope scope) {
        DevUiAgentListener owner = null;
        for (DevUiAgentListener listener : listeners()) {
            Run run = listener.run(scope);
            if (run != null && run.origin() == RunOrigin.DEV_UI) {
                return listener;
            }
            if (run != null && owner == null) {
                owner = listener;
            }
        }
        return owner;
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    @Override
    public void afterAgenticScopeCreated(AgenticScope scope) {
        listeners().forEach(listener -> listener.afterAgenticScopeCreated(scope));
    }

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope scope) {
        listeners().forEach(listener -> listener.beforeAgenticScopeDestroyed(scope));
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        listeners().forEach(listener -> listener.beforeAgentInvocation(request, systemId));
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        listeners().forEach(listener -> listener.afterAgentInvocation(response));
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        listeners().forEach(listener -> listener.onAgentInvocationError(error));
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution execution) {
        listeners().forEach(listener -> listener.beforeAgentToolExecution(execution));
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution execution) {
        listeners().forEach(listener -> listener.afterAgentToolExecution(execution));
    }
}
