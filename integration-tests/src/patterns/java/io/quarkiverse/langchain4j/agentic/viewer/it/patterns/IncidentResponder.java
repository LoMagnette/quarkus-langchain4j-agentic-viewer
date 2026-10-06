package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.patterns.blackboard.ConflictResolutionStrategy;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Blackboard: knowledge sources post partial results to the scope; one ready agent fires at a time until the
 * goal key {@code fix} is present. Both analyzers are ready at the start: the conflict resolution strategy picks
 * the metrics analyzer first although the log analyzer is declared first.
 */
public interface IncidentResponder {

    @PlannerAgent(description = "Knowledge sources collaborating on a shared blackboard to fix an incident", outputKey = "fix", subAgents = {
            LogAnalyzer.class, MetricsAnalyzer.class, Diagnoser.class, FixProposer.class })
    String respond(@V("incident") String incident);

    @PlannerSupplier
    static Planner planner() {
        return new BlackboardPlanner(metricsFirst());
    }

    /**
     * Prefers the metrics analyzer when it is ready, otherwise declaration order.
     * (ConflictResolutionStrategy.agentWithName returns no agent when the named one is not ready, which would stop the planner.)
     */
    static ConflictResolutionStrategy metricsFirst() {
        return (scope, candidates) -> candidates.stream()
                .filter(agent -> agent.name().equals("analyzeMetrics"))
                .findFirst()
                .orElse(candidates.get(0));
    }

    interface LogAnalyzer {
        @UserMessage("Analyze the logs for this incident: {{incident}}")
        @Agent(description = "Finds relevant log entries", outputKey = "findings")
        String analyzeLogs(@V("incident") String incident);
    }

    interface MetricsAnalyzer {
        @UserMessage("Analyze the metrics for this incident: {{incident}}")
        @Agent(description = "Finds anomalies in the metrics", outputKey = "metrics")
        String analyzeMetrics(@V("incident") String incident);
    }

    interface Diagnoser {
        @UserMessage("Diagnose the root cause from logs ({{findings}}) and metrics ({{metrics}})")
        @Agent(description = "Diagnoses the root cause", outputKey = "diagnosis")
        String diagnose(@V("findings") String findings, @V("metrics") String metrics);
    }

    interface FixProposer {
        @UserMessage("Propose a fix for: {{diagnosis}}")
        @Agent(description = "Proposes a fix", outputKey = "fix")
        String proposeFix(@V("diagnosis") String diagnosis);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Analyze the logs for this incident:")).reply("OutOfMemoryError in the order service")
                    .when(userMessageContains("Analyze the metrics for this incident:")).reply("heap usage at 98%")
                    .when(userMessageContains("Diagnose the root cause")).reply("the order cache grows without bounds")
                    .when(userMessageContains("Propose a fix for:")).reply("Bound the order cache to 10,000 entries");
        }
    }
}
