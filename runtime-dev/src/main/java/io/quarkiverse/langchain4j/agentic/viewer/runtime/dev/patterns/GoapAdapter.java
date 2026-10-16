package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedSearchGraph;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * Goal-oriented (GOAP) planners of langchain4j-agentic-patterns. Registered only when the module is present.
 * <p>
 * The planner searches, among its sub-agents linked by input and output keys, the shortest chain producing its goal
 * key (its agent's output key) from the keys of the scope, then calls that chain. The adapter runs the same search
 * (GoalOrientedSearchGraph, which only reads argument names and output keys): on the real sub-agents at the first
 * decision for the live path, and on stand-ins built from the topology for a preview.
 */
@ApplicationScoped
public class GoapAdapter implements PatternAdapter {

    @Override
    public PatternType patternType() {
        return PatternType.GOAP;
    }

    @Override
    public boolean handles(Planner planner) {
        return planner instanceof GoalOrientedPlanner;
    }

    @Override
    public Insight insight(Decision decision) {
        if (!decision.first() || decision.scope() == null || decision.plannerAgent() == null) {
            return Insight.NONE;
        }
        List<AgentInstance> path = search(decision.subagents(), decision.scope().state().keySet(),
                decision.plannerAgent().outputKey());
        return path == null ? Insight.NONE : new Insight(Map.of("goal", decision.plannerAgent().outputKey()), path);
    }

    @Override
    public Optional<List<String>> previewPath(AgentNode plannerNode, Collection<String> presentKeys) {
        List<AgentInstance> stands = plannerNode.children().stream().map(child -> (AgentInstance) new StandIn(child)).toList();
        List<AgentInstance> path = search(stands, presentKeys, plannerNode.outputKey());
        return path == null ? Optional.empty() : Optional.of(path.stream().map(AgentInstance::agentId).toList());
    }

    private static List<AgentInstance> search(List<AgentInstance> agents, Collection<String> keys, String goal) {
        if (goal == null) {
            return null;
        }
        try {
            return new GoalOrientedSearchGraph(agents).search(keys, goal);
        } catch (RuntimeException e) {
            return null; // no path (or an unexpected graph): nothing to tell
        }
    }

    /** A sub-agent as the topology describes it: what the search reads (argument names, output key), id = node id. */
    private record StandIn(AgentNode node) implements AgentInstance {

        @Override
        public Class<?> type() {
            return Object.class;
        }

        @Override
        public Class<? extends Planner> plannerType() {
            return null;
        }

        @Override
        public String name() {
            return node.name();
        }

        @Override
        public String agentId() {
            return node.id();
        }

        @Override
        public String description() {
            return node.description();
        }

        @Override
        public Type outputType() {
            return Object.class;
        }

        @Override
        public String outputKey() {
            return node.outputKey();
        }

        @Override
        public boolean async() {
            return node.async();
        }

        @Override
        public List<AgentArgument> arguments() {
            return node.inputKeys().stream().map(key -> new AgentArgument(Object.class, key)).toList();
        }

        @Override
        public AgentInstance parent() {
            return null;
        }

        @Override
        public List<AgentInstance> subagents() {
            return List.of();
        }

        @Override
        public AgenticSystemTopology topology() {
            return null;
        }
    }
}
