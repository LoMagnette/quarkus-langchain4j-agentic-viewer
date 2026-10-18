package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.PatternAdapters;
import io.quarkus.runtime.StartupEvent;

/**
 * At dev-mode startup, attaches the Dev UI listener to every known root agent, so Dev UI runs report their agent
 * events.
 */
@ApplicationScoped
public class RunObservation {

    private static final Logger LOG = Logger.getLogger(RunObservation.class);

    @Inject
    RunManager runManager;

    @Inject
    PatternAdapters patternAdapters;

    @Inject
    AgenticSystemRegistry registry;

    // one listener for the declarative roots and the builder ones
    private DevUiAgentListener listener;
    private boolean attached;

    private synchronized DevUiAgentListener listener() {
        if (listener == null) {
            listener = new DevUiAgentListener(runManager, patternAdapters);
        }
        return listener;
    }

    /**
     * Observes the root of a builder system when it is found (T5.3): its runs then report their agent events, human
     * steps and planner decisions like the declarative ones.
     */
    public void observe(String systemId, Object root) {
        try {
            RootListeners.attach(systemId, root, listener());
        } catch (RuntimeException e) {
            LOG.warnf(e, "Agentic viewer: cannot observe %s, its runs will show no agent events", systemId);
        }
    }

    void attach(@Observes StartupEvent startup) {
        ensureObserved();
    }

    /**
     * Attaches the Dev UI listener to the declarative roots, once: at startup, or before a run asked for earlier (the
     * Dev UI may start a run right after a hot reload, before the startup observers of the new application ran).
     */
    public synchronized void ensureObserved() {
        if (attached) {
            return;
        }
        attached = true;
        // the live view relies on internal parts of langchain4j-agentic: tell at once what a library upgrade broke
        List<String> missing = LangChain4jInternals.problems(Thread.currentThread().getContextClassLoader());
        if (!missing.isEmpty()) {
            LOG.warnf("Agentic viewer: this version of langchain4j-agentic (or langchain4j-agentic-patterns,"
                    + " quarkus-langchain4j-agentic) lacks internal parts the Dev UI runs rely on; the live information"
                    + " named for each is missing:%n  %s", String.join("%n  ".formatted(), missing));
        }
        // declarative systems only: builder system beans are created when the Dev UI asks for them
        RootListeners.attach(registry.buildTimeSystems(), listener());
    }
}
