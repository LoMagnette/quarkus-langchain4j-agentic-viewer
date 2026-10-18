package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;

/**
 * Attaches a listener to the root agent of each system, inherited by all its sub-agents. A Dev UI listener
 * also gets the human-in-the-loop steps of its runs ({@link HumanInTheLoopSteps}) and the planner decisions
 * ({@link PlannerDecisions}).
 * <p>
 * Uses the internal {@link InternalAgent#registerInheritedParentListener} (T0.4 finding, Backlog doc-3): CDI
 * AgentListener beans only reach AI agents. A listener cannot be removed: the Dev UI hooks are installed once per root
 * instance ({@link DevUiListeners}), further Dev UI listeners join them (a hot reload creates new roots).
 */
public final class RootListeners {

    private static final Logger LOG = Logger.getLogger(RootListeners.class);
    /** The Dev UI hooks of each root, weakly: a root of a previous application (hot reload) goes away. */
    private static final Map<Object, DevUiListeners> HOOKS = Collections.synchronizedMap(new WeakHashMap<>());

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
                if (attach(system.id(), bean == null ? null : ClientProxy.unwrap(bean), listener)) {
                    attached++;
                }
            } catch (ClassNotFoundException | RuntimeException e) {
                LOG.warnf(e, "Agentic viewer: cannot observe %s, its runs will show no agent events", system.id());
            }
        }
        return attached;
    }

    /**
     * Attaches a listener to a root agent: a declarative root bean, or the root of a builder system (T5.3), already
     * built: the same post-build hooks apply.
     *
     * @return whether it is an agent the listener could be attached to
     */
    public static boolean attach(String systemId, Object root, AgentListener listener) {
        if (!(root instanceof InternalAgent agent)) {
            LOG.debugf("Agentic viewer: no agent bean to observe for %s", systemId);
            return false;
        }
        if (!(listener instanceof DevUiAgentListener devUiListener)) {
            agent.registerInheritedParentListener(listener);
            return true;
        }
        DevUiListeners hooks;
        synchronized (HOOKS) {
            hooks = HOOKS.get(root);
            if (hooks == null) {
                hooks = new DevUiListeners();
                agent.registerInheritedParentListener(hooks);
                HumanInTheLoopSteps.intercept(root, hooks);
                PlannerDecisions.decorate(root, hooks);
                HOOKS.put(root, hooks);
            }
        }
        hooks.add(devUiListener);
        return true;
    }
}
