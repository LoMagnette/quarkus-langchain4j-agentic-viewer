package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgenticGraph;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Runtime recorder for the Agentic Viewer extension.
 */
@Recorder
public class AgenticViewerRecorder {

    private static volatile AgenticGraph graph;

    /**
     * Sets the agentic graph at runtime.
     *
     * @param agenticGraph the graph built during deployment
     */
    public void setAgenticGraph(AgenticGraph agenticGraph) {
        if (agenticGraph != null) {
            graph = agenticGraph;
        }
    }

    /**
     * Gets the agentic graph.
     *
     * @return the current agentic graph
     */
    public static AgenticGraph getAgenticGraph() {
        return graph != null ? graph : AgenticGraph.empty();
    }
}
