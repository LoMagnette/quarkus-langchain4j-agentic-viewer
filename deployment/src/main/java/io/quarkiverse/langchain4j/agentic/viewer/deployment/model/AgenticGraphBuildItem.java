package io.quarkiverse.langchain4j.agentic.viewer.deployment.model;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgenticGraph;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item containing the discovered agentic graph.
 */
public final class AgenticGraphBuildItem extends SimpleBuildItem {

    private final AgenticGraph graph;

    public AgenticGraphBuildItem(AgenticGraph graph) {
        this.graph = graph;
    }

    public AgenticGraph getGraph() {
        return graph;
    }
}
