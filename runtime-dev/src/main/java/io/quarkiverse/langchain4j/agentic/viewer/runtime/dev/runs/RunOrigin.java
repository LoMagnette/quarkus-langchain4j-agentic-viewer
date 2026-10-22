package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

/**
 * Who started a run.
 */
public enum RunOrigin {
    /** The run form of the Dev UI: executed by the run manager on its own thread. */
    DEV_UI,
    /** The application itself (e.g. a REST endpoint): observed only. */
    APPLICATION
}
