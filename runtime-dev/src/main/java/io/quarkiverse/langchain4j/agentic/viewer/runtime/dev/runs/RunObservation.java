package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkus.runtime.StartupEvent;

/**
 * At dev-mode startup, attaches the Dev UI listener to every known root agent, so Dev UI runs report their agent
 * events.
 */
@ApplicationScoped
public class RunObservation {

    void attach(@Observes StartupEvent startup, AgenticSystemRegistry registry, RunManager runManager) {
        RootListeners.attach(registry.systems(), new DevUiAgentListener(runManager));
    }
}
