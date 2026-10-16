package io.quarkiverse.langchain4j.agentic.viewer.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runs of agentic systems started from the Dev UI (dev mode only).
 */
@ConfigMapping(prefix = "quarkus.langchain4j.agentic.dev-ui")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface AgenticViewerRuntimeConfig {

    /**
     * Runs kept per agentic system in the run history of the Dev UI; when a run starts beyond this number, the oldest
     * ended runs are dropped.
     */
    @WithDefault("20")
    int historySize();

    /**
     * Maximum size, in characters of JSON, of a value carried by a run event (inputs, outputs, scope values, tool
     * arguments and results); a longer value is truncated, and marked as such in the Dev UI.
     */
    @WithDefault("2048")
    int maxPayloadSize();
}
