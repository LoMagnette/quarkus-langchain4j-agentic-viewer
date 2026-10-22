package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.Map;

/**
 * A series as listed with the other series of its system.
 *
 * @param started runs started so far
 */
public record SeriesSummary(String seriesId, String systemId, Map<String, Object> inputs, int count, int started,
        RunStatus status, long startedAt, Long durationMillis) {

    static SeriesSummary of(Series series) {
        return new SeriesSummary(series.id(), series.systemId(), series.inputs(), series.count(), series.runIds().size(),
                series.status(), series.startedAt(), series.durationMillis());
    }
}
