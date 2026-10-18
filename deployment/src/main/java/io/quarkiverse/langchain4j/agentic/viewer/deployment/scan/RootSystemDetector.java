package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;

/**
 * Finds the agentic systems of the application: an agent whose class no other agent lists as a sub-agent is the
 * root of a system; every other agent appears only nested in the systems that use it. A supertype whose agent method
 * a class inherits is not a system of its own.
 */
public class RootSystemDetector {

    /**
     * The agent method langchain4j takes for a class with several (AgenticServices.createBuiltInAgentExecutor): the
     * first annotation found in this order, then an {@code @Agent} method.
     */
    private static final List<NodeKind> KIND_PRIORITY = List.of(NodeKind.SEQUENCE, NodeKind.LOOP, NodeKind.CONDITIONAL,
            NodeKind.PARALLEL, NodeKind.PARALLEL_MAPPER, NodeKind.SUPERVISOR, NodeKind.PLANNER,
            NodeKind.HUMAN_IN_THE_LOOP, NodeKind.A2A_CLIENT_AGENT, NodeKind.REGISTRY_AGENT, NodeKind.MCP_CLIENT_AGENT,
            NodeKind.AI_AGENT, NodeKind.NON_AI_AGENT);

    /**
     * @param descriptors every agent method, as produced by {@link AgenticAnnotationScanner}
     * @return one tree per root system, sorted by root id
     */
    public List<AgentTree> detect(List<AgentDescriptor> descriptors) {
        List<AgentDescriptor> sorted = descriptors.stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
        // sub-agents are referenced by class; a class with several agent methods resolves like langchain4j does
        Map<String, AgentDescriptor> byClass = new LinkedHashMap<>();
        Set<String> referenced = new HashSet<>();
        for (AgentDescriptor descriptor : sorted.stream()
                .sorted(Comparator.comparingInt((AgentDescriptor d) -> KIND_PRIORITY.indexOf(d.kind()))).toList()) {
            byClass.putIfAbsent(descriptor.declaringClass(), descriptor);
            referenced.addAll(descriptor.subAgents());
            if (descriptor.inheritedFrom() != null) {
                referenced.add(descriptor.inheritedFrom());
            }
        }
        List<AgentTree> roots = new ArrayList<>();
        for (AgentDescriptor descriptor : sorted) {
            if (!referenced.contains(descriptor.declaringClass())) {
                roots.add(tree(descriptor.declaringClass(), descriptor, byClass, new HashSet<>()));
            }
        }
        return roots;
    }

    private static AgentTree tree(String agentClass, AgentDescriptor descriptor, Map<String, AgentDescriptor> byClass,
            Set<String> ancestors) {
        if (descriptor == null || !ancestors.add(agentClass)) {
            return new AgentTree(agentClass, descriptor, List.of());
        }
        List<AgentTree> children = new ArrayList<>();
        for (String subAgent : descriptor.subAgents()) {
            // langchain4j leaves out a conditional's sub-agent that no activation condition lists
            if (descriptor.kind() != NodeKind.CONDITIONAL || descriptor.branchGuards().containsKey(subAgent)) {
                children.add(tree(subAgent, byClass.get(subAgent), byClass, ancestors));
            }
        }
        ancestors.remove(agentClass);
        return new AgentTree(agentClass, descriptor, children);
    }
}
