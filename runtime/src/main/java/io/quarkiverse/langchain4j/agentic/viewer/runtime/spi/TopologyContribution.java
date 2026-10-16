package io.quarkiverse.langchain4j.agentic.viewer.runtime.spi;

import java.util.List;

/**
 * What an {@link AgenticTopologyContributor} draws inside a planner node.
 *
 * @param nodes extra nodes (e.g. states or outcomes of the planner)
 * @param edges edges between the planner's sub-agents and the extra nodes
 */
public record TopologyContribution(List<ContributedNode> nodes, List<ContributedEdge> edges) {

    public TopologyContribution {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
