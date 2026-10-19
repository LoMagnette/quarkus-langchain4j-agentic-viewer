package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;

/**
 * What the system list shows about an agentic system.
 *
 * @param id system id
 * @param name display name
 * @param origin how the system is defined
 * @param sourceRef where the system is defined
 * @param rootKind kind of the root node (an agent kind for a system of one agent)
 * @param patternKinds kinds of the workflows and steps it uses (not agents), in tree order, without duplicates
 * @param plannerPatterns pattern of each of its {@link NodeKind#PLANNER} nodes ({@link PatternType#CUSTOM} when not a
 *        langchain4j-agentic-patterns planner), in tree order, without duplicates: the list shows them for PLANNER
 * @param inputCount number of inputs of its run form
 */
public record AgenticSystemSummary(String id, String name, SystemOrigin origin, SourceRef sourceRef,
        NodeKind rootKind, List<NodeKind> patternKinds, List<PatternType> plannerPatterns, int inputCount) {

    private static final Set<NodeKind> AGENT_KINDS = Set.of(NodeKind.AI_AGENT, NodeKind.NON_AI_AGENT,
            NodeKind.A2A_CLIENT_AGENT, NodeKind.MCP_CLIENT_AGENT, NodeKind.REGISTRY_AGENT);

    public AgenticSystemSummary {
        patternKinds = patternKinds == null ? List.of() : List.copyOf(patternKinds);
        plannerPatterns = plannerPatterns == null ? List.of() : List.copyOf(plannerPatterns);
    }

    public static AgenticSystemSummary of(AgenticSystem system) {
        List<NodeKind> kinds = new ArrayList<>();
        List<PatternType> patterns = new ArrayList<>();
        collectPatternKinds(system.root(), kinds, patterns);
        return new AgenticSystemSummary(system.id(), system.name(), system.origin(), system.sourceRef(),
                system.root().kind(), kinds, patterns, system.inputs().size());
    }

    private static void collectPatternKinds(AgentNode node, List<NodeKind> kinds, List<PatternType> patterns) {
        if (!AGENT_KINDS.contains(node.kind()) && !kinds.contains(node.kind())) {
            kinds.add(node.kind());
        }
        if (node.kind() == NodeKind.PLANNER) {
            PatternType pattern = node.plannerInfo() == null || node.plannerInfo().patternType() == null ? PatternType.CUSTOM
                    : node.plannerInfo().patternType();
            if (!patterns.contains(pattern)) {
                patterns.add(pattern);
            }
        }
        node.children().forEach(child -> collectPatternKinds(child, kinds, patterns));
    }
}
