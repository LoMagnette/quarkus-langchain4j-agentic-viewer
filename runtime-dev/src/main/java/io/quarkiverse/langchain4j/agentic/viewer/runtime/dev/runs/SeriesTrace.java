package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.List;
import java.util.Map;

/**
 * A series with the results of its runs so far.
 *
 * @param count runs asked for
 * @param status RUNNING, SUCCEEDED (every run ended), CANCELLED (stopped) or FAILED (a run could not start: error)
 * @param runs its runs, in order
 */
public record SeriesTrace(String seriesId, String systemId, Map<String, Object> inputs, int count, RunStatus status,
        long startedAt, Long durationMillis, String error, List<SeriesRun> runs) {
}
