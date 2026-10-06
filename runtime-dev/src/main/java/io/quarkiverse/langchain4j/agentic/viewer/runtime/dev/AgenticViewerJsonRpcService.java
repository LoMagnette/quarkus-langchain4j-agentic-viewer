package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgenticGraph;

/**
 * JSON-RPC service for the Agentic Viewer Dev UI.
 * Provides endpoints to fetch the agentic graph data.
 */
public class AgenticViewerJsonRpcService {

    /**
     * Gets the complete agentic graph.
     *
     * @return the agentic graph
     */
    public AgenticGraph getGraph() {
        return AgenticViewerRecorder.getAgenticGraph();
    }

    /**
     * Gets the count of agents in the graph.
     *
     * @return the number of agents
     */
    public int getAgentCount() {
        return AgenticViewerRecorder.getAgenticGraph().agents().size();
    }

    /**
     * Gets the count of tools in the graph.
     *
     * @return the number of tools
     */
    public int getToolCount() {
        return AgenticViewerRecorder.getAgenticGraph().tools().size();
    }

    /**
     * Checks if imperative patterns were detected.
     *
     * @return true if imperative patterns were found
     */
    public boolean hasImperativePatterns() {
        return AgenticViewerRecorder.getAgenticGraph().hasImperativePatterns();
    }
}
