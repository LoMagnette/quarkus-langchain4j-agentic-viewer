package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import java.util.List;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.AgenticTopologyContributor;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.ContributedEdge;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.ContributedNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.TopologyContribution;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;

/**
 * Tells the Dev UI how {@link DraftReviewer.DraftReviewPlanner} works: draft, review, back to drafting when rejected,
 * done when approved or after {@value DraftReviewer.DraftReviewPlanner#MAX_DRAFTS} drafts.
 */
public class DraftReviewTopology implements AgenticTopologyContributor {

    @Override
    public Class<?> plannerAgent() {
        return DraftReviewer.class;
    }

    @Override
    public TopologyContribution contribute(AgentNode plannerNode) {
        return new TopologyContribution(
                List.of(new ContributedNode("done", "approved or " + DraftReviewer.DraftReviewPlanner.MAX_DRAFTS + " drafts")),
                List.of(new ContributedEdge("writeDraft", "review", "draft"),
                        new ContributedEdge("review", "writeDraft", "rejected"),
                        new ContributedEdge("review", "done", "approved")));
    }
}
