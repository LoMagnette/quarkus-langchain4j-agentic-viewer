package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.List;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;

/**
 * A run with every event it emitted, to reopen and replay it.
 */
public record RunTrace(RunSummary summary, List<ExecutionEvent> events) {
}
