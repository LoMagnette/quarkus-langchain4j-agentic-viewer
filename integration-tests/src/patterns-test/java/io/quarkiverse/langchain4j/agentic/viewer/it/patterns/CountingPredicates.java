package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.patterns.p2p.P2PPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.V;

/**
 * Test-only pattern systems whose exit condition and goal count their calls: the Dev UI must not call them more.
 */
public interface CountingPredicates {

    AtomicInteger EXIT_CALLS = new AtomicInteger();
    AtomicInteger GOAL_CALLS = new AtomicInteger();

    interface CountingSearcher {
        @PlannerAgent(outputKey = "papers", subAgents = { ResearchTeam.LiteratureSearcher.class })
        String search(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner() {
            return new P2PPlanner(10, scope -> {
                EXIT_CALLS.incrementAndGet();
                return false;
            });
        }
    }

    interface CountingBoard {
        @PlannerAgent(outputKey = "findings", subAgents = { IncidentResponder.LogAnalyzer.class })
        String watch(@V("incident") String incident);

        @PlannerSupplier
        static Planner planner() {
            return new BlackboardPlanner(scope -> {
                GOAL_CALLS.incrementAndGet();
                return false;
            });
        }
    }
}
