package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.List;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;

/**
 * Attaches a listener to the root agent of each declarative system, inherited by all its sub-agents. A Dev UI listener
 * also gets the human-in-the-loop steps of its runs ({@link HumanInTheLoopSteps}) and the planner decisions
 * ({@link PlannerDecisions}).
 * <p>
 * Uses the internal {@link InternalAgent#registerInheritedParentListener} (T0.4 finding, Backlog doc-3): CDI
 * AgentListener beans only reach AI agents. A listener cannot be removed: attach it once per root instance (a hot
 * reload creates new ones).
 */
public final class RootListeners {

    private static final Logger LOG = Logger.getLogger(RootListeners.class);

    private RootListeners() {
    }

    /**
     * @return the number of roots the listener was attached to
     */
    public static int attach(List<AgenticSystem> systems, AgentListener listener) {
        int attached = 0;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (AgenticSystem system : systems) {
            try {
                Class<?> rootType = Class.forName(system.sourceRef().className(), true, classLoader);
                Object bean = Arc.container().instance(rootType).get();
                Object root = bean == null ? null : ClientProxy.unwrap(bean);
                if (root instanceof InternalAgent agent) {
                    agent.registerInheritedParentListener(listener);
                    if (listener instanceof DevUiAgentListener devUiListener) {
                        HumanInTheLoopSteps.intercept(root, devUiListener);
                        PlannerDecisions.decorate(root, devUiListener);
                    }
                    attached++;
                } else {
                    LOG.debugf("Agentic viewer: no agent bean to observe for %s", system.id());
                }
            } catch (ClassNotFoundException | RuntimeException e) {
                LOG.warnf(e, "Agentic viewer: cannot observe %s, its runs will show no agent events", system.id());
            }
        }
        return attached;
    }
}
