package io.quarkiverse.langchain4j.agentic.viewer.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runs of agentic systems in the Dev UI: started from it, or by the application (dev mode only).
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

    /**
     * Whether the runs the application starts itself (a REST endpoint or a scheduled job calling an agentic system) are
     * recorded and shown in the Dev UI, next to the runs started from it. They are observed only: their
     * human-in-the-loop steps call the application's method and they cannot be cancelled from the Dev UI. The Dev UI
     * can pause the recording; {@code false} turns it off entirely. A builder system is observed once the Dev UI listed
     * it.
     */
    @WithDefault("true")
    boolean applicationRuns();

    /**
     * Ended application runs kept per agentic system, apart from the runs started from the Dev UI (which they never
     * push out): beyond this number the oldest ended application runs are dropped.
     */
    @WithDefault("20")
    int applicationHistorySize();

    /**
     * Application runs of one agentic system recorded at the same time: beyond this number a new application run is
     * not recorded (it runs as usual) and the Dev UI counts it as skipped, so a load test never floods the viewer.
     */
    @WithDefault("16")
    int maxOpenApplicationRuns();
}
