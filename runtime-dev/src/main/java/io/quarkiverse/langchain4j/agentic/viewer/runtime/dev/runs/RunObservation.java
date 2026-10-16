package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

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


    void attach(@Observes StartupEvent startup, AgenticSystemRegistry registry, RunManager runManager,
            PatternAdapters patternAdapters) {
        // the live view relies on internal parts of langchain4j-agentic: tell at once what a library upgrade broke
        List<String> missing = LangChain4jInternals.problems(Thread.currentThread().getContextClassLoader());
        if (!missing.isEmpty()) {
            LOG.warnf("Agentic viewer: this version of langchain4j-agentic (or langchain4j-agentic-patterns,"
                    + " quarkus-langchain4j-agentic) lacks internal parts the Dev UI runs rely on; the live information"
                    + " named for each is missing:%n  %s", String.join("%n  ".formatted(), missing));
        }
        RootListeners.attach(registry.systems(), new DevUiAgentListener(runManager, patternAdapters));
    }
}
