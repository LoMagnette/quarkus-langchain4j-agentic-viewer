package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

/**
 * Whether the runs the application starts itself are recorded.
 *
 * @param enabled whether they are recorded at all ({@code quarkus.langchain4j.agentic.dev-ui.application-runs})
 * @param recording whether the recording is on (the Dev UI may pause it)
 * @param skipped application runs of the system not recorded: too many open at once, or the viewer falling behind
 */
public record ApplicationRuns(boolean enabled, boolean recording, long skipped) {
}
