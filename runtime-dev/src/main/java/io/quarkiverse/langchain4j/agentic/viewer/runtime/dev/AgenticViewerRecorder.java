package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.List;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgenticGraph;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Runtime recorder for the Agentic Viewer extension.
 */
@Recorder
public class AgenticViewerRecorder {

    private static volatile AgenticGraph graph;
    private static volatile List<AgenticSystem> systems = List.of();

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

    /**
     * Sets the agentic systems known at build time (declarative ones).
     */
    public void setSystems(List<AgenticSystem> buildTimeSystems) {
        systems = List.copyOf(buildTimeSystems);
    }

    /**
     * @return the agentic systems known at build time, empty before static init
     */
    public static List<AgenticSystem> getSystems() {
        return systems;
    }
}
