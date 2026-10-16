package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * What the Dev UI knows about a planner of langchain4j-agentic-patterns (F5). Implementations reference the patterns
 * classes: they are registered as beans only when the module is on the class path (AgenticViewerProcessor), and nothing
 * else refers to them, so they are never loaded without it. This interface does not refer to the module.
 */
public interface PatternAdapter {

    /**
     * A decision of a planner this adapter handles.
     *
     * @param plannerAgent the agent the planner plans for (its output key is the goal of a GOAP planner)
     * @param subagents the agents the planner can call, in declaration order
     * @param first whether it is the first decision of the planner's invocation
     */
    record Decision(Planner planner, Action action, AgenticScope scope, AgentInstance plannerAgent,
            List<AgentInstance> subagents, boolean first) {
    }

    /**
     * What an adapter tells about a decision.
     *
     * @param extras pattern-specific information, added to the extras of the PlannerDecision event
     * @param path agents the planner intends to call, in order (e.g. the path a GOAP planner computed), null if none
     */
    record Insight(Map<String, Object> extras, List<AgentInstance> path) {

        public static final Insight NONE = new Insight(Map.of(), null);
    }

    /**
     * @return the pattern of the planners this adapter handles
     */
    PatternType patternType();

    /**
     * @return whether the planner is one this adapter handles
     */
    boolean handles(Planner planner);

    /**
     * Pattern-specific information on a decision of the planner (GOAP path, P2P activations, blackboard candidates).
     */
    default Insight insight(Decision decision) {
        return Insight.NONE;
    }

    /**
     * The sub-agents a planner of this pattern would call, before running it.
     *
     * @param plannerNode the planner node of the topology
     * @param presentKeys AgenticScope keys known when the planner starts (e.g. the run inputs)
     * @return node ids of the planner node's children in call order; empty when the pattern cannot tell
     */
    default Optional<List<String>> previewPath(AgentNode plannerNode, Collection<String> presentKeys) {
        return Optional.empty();
    }
}
