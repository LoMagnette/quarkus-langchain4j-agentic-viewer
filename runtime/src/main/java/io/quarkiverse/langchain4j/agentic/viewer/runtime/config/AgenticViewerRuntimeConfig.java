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
     * Ended runs kept per agentic system in the run history of the Dev UI, with their events: beyond this number the
     * oldest ended runs are dropped. Runs still running are always kept, and dropped once ended if the history is
     * full.
     */
    @WithDefault("20")
    int historySize();

    /**
     * Maximum size, in characters of JSON, of a value carried by a run event (inputs, outputs, scope values, tool
     * arguments and results); a longer value is truncated, and marked as such in the Dev UI.
     */
    @WithDefault("2048")
    int maxPayloadSize();

    /**
     * Runs started from the Dev UI that may execute at the same time, each on its own thread: beyond this number a new
     * run is refused until one ends. A cancelled run counts until its thread returns (a model or tool call may ignore
     * the interruption).
     */
    @WithDefault("16")
    int maxConcurrentRuns();
}
