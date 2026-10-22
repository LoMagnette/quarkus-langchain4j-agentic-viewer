package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.Map;

/**
 * A run as listed in the run history.
 *
 * @param runId run id
 * @param systemId system the run belongs to
 * @param status current status
 * @param startedAt start time in milliseconds since the epoch
 * @param durationMillis duration of an ended run, null while running
 * @param inputs input values by name
 * @param origin who started the run: the Dev UI, or the application
 */
public record RunSummary(String runId, String systemId, RunStatus status, long startedAt, Long durationMillis,
        Map<String, Object> inputs, RunOrigin origin) {

    static RunSummary of(Run run) {
        return new RunSummary(run.id(), run.systemId(), run.status(), run.startedAt(), run.durationMillis(), run.inputs(),
                run.origin());
    }
}
