package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.p2p.P2PPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.V;

/**
 * Test-only peer-to-peer systems built from the research team's agents, stopping for the other two reasons.
 */
public interface P2PStopSystems {

    /** The critic and the refiner keep answering each other: stops at the maximum number of invocations. */
    interface EndlessDebate {
        @PlannerAgent(outputKey = "hypothesis", subAgents = { ResearchTeam.LiteratureSearcher.class,
                ResearchTeam.HypothesisFormulator.class, ResearchTeam.Critic.class, ResearchTeam.Refiner.class })
        String debate(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner() {
            return new P2PPlanner(4, scope -> false);
        }
    }

    /** Only the literature search can run: once done, no agent can activate (a stable state). */
    interface LoneSearcher {
        @PlannerAgent(outputKey = "papers", subAgents = { ResearchTeam.LiteratureSearcher.class })
        String search(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner() {
            return new P2PPlanner(10, scope -> false);
        }
    }
}
