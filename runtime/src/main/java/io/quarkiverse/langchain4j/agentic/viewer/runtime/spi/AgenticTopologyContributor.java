package io.quarkiverse.langchain4j.agentic.viewer.runtime.spi;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;

/**
 * Describes the structure of a custom planner for the Dev UI, which otherwise draws its sub-agents as candidates with
 * no order: what a planner does is code, the viewer cannot infer it.
 * <p>
 * Implement it in the application with a public no-argument constructor: the Dev UI finds the implementations at build
 * time and calls them in dev mode only (nothing runs in production). Each one describes the planner nodes of one
 * {@code @PlannerAgent} interface, the one declaring its {@code @PlannerSupplier}.
 *
 * <pre>{@code
 * public class DraftReviewTopology implements AgenticTopologyContributor {
 *
 *     public Class<?> plannerAgent() {
 *         return DraftReviewer.class;
 *     }
 *
 *     public TopologyContribution contribute(AgentNode plannerNode) {
 *         return new TopologyContribution(
 *                 List.of(new ContributedNode("approved", "approved or 3 drafts")),
 *                 List.of(new ContributedEdge("writeDraft", "review", "draft"),
 *                         new ContributedEdge("review", "writeDraft", "rejected"),
 *                         new ContributedEdge("review", "approved", "approved")));
 *     }
 * }
 * }</pre>
 */
public interface AgenticTopologyContributor {

    /**
     * @return the {@code @PlannerAgent} interface whose planner this contributor describes
     */
    Class<?> plannerAgent();

    /**
     * What to draw inside a planner node, next to its sub-agents.
     *
     * @param plannerNode the planner node, with its sub-agents as children
     * @return extra nodes and edges; edges link sub-agents (by agent name or node id) and contributed nodes (by id)
     */
    TopologyContribution contribute(AgentNode plannerNode);
}
