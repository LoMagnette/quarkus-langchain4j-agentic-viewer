package io.quarkiverse.langchain4j.agentic.viewer.test.topology;

import java.util.List;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;

/**
 * Receives topology values recorded at build time (see {@link TopologyRecordingTest}).
 */
public class TopologyTestRecorder {

    public static volatile List<AgenticSystem> recorded;

    public void record(List<AgenticSystem> systems) {
        recorded = systems;
    }
}
