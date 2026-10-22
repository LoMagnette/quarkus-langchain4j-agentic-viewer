package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.List;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorInfo;

/**
 * One run of a series, as its results show it.
 *
 * @param index position in the series, from 1
 * @param status current status
 * @param durationMillis duration of an ended run, null while running
 * @param tokens total tokens of its agents (0 when the models tell none)
 * @param output the output of a succeeded run, bounded like the events' payloads
 * @param error the failure of a failed or cancelled run
 * @param path the topology node ids of the agents it invoked, in start order (a loop repeats its nodes)
 */
public record SeriesRun(int index, String runId, RunStatus status, Long durationMillis, long tokens, Object output,
        ErrorInfo error, List<String> path) {
}
