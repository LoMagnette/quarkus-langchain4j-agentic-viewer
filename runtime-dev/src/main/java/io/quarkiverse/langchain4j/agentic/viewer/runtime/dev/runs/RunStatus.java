package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

/**
 * Where a run is in its life.
 */
public enum RunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    /** ended by cancelRun; the system may still be busy in a call that ignores interruption */
    CANCELLED
}
