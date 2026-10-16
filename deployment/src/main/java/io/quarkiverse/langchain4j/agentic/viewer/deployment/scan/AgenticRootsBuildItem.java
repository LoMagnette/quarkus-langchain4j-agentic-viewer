package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.List;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The root agentic systems of the application, with their sub-agents nested, found by {@link RootSystemDetector}.
 */
public final class AgenticRootsBuildItem extends SimpleBuildItem {

    private final List<AgentTree> roots;

    public AgenticRootsBuildItem(List<AgentTree> roots) {
        this.roots = List.copyOf(roots);
    }

    public List<AgentTree> getRoots() {
        return roots;
    }
}
