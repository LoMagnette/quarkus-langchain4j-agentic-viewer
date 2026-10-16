package io.quarkiverse.langchain4j.agentic.viewer.deployment.topology;

import java.util.List;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The agentic systems known at build time (declarative ones), built by {@link TopologyBuilder}.
 */
public final class AgenticSystemsBuildItem extends SimpleBuildItem {

    private final List<AgenticSystem> systems;

    public AgenticSystemsBuildItem(List<AgenticSystem> systems) {
        this.systems = List.copyOf(systems);
    }

    public List<AgenticSystem> getSystems() {
        return systems;
    }
}
