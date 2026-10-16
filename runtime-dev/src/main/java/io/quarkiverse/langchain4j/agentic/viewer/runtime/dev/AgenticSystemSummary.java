package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
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
 * @param inputCount number of inputs of its run form
 */
public record AgenticSystemSummary(String id, String name, SystemOrigin origin, SourceRef sourceRef,
        NodeKind rootKind, List<NodeKind> patternKinds, int inputCount) {

    private static final Set<NodeKind> AGENT_KINDS = Set.of(NodeKind.AI_AGENT, NodeKind.NON_AI_AGENT,
            NodeKind.A2A_CLIENT_AGENT, NodeKind.MCP_CLIENT_AGENT);

    public AgenticSystemSummary {
        patternKinds = patternKinds == null ? List.of() : List.copyOf(patternKinds);
    }

    public static AgenticSystemSummary of(AgenticSystem system) {
        List<NodeKind> kinds = new ArrayList<>();
        collectPatternKinds(system.root(), kinds);
        return new AgenticSystemSummary(system.id(), system.name(), system.origin(), system.sourceRef(),
                system.root().kind(), kinds, system.inputs().size());
    }

    private static void collectPatternKinds(AgentNode node, List<NodeKind> kinds) {
        if (!AGENT_KINDS.contains(node.kind()) && !kinds.contains(node.kind())) {
            kinds.add(node.kind());
        }
        node.children().forEach(child -> collectPatternKinds(child, kinds));
    }
}
