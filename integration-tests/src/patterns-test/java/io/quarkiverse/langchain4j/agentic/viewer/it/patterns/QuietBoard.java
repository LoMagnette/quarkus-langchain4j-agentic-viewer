package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.V;

/**
 * Test-only blackboard whose goal never holds: once the log analyzer fired, no agent can fire (quiescent).
 */
public interface QuietBoard {

    @PlannerAgent(outputKey = "findings", subAgents = { IncidentResponder.LogAnalyzer.class })
    String watch(@V("incident") String incident);

    @PlannerSupplier
    static Planner planner() {
        return new BlackboardPlanner(scope -> false);
    }
}
