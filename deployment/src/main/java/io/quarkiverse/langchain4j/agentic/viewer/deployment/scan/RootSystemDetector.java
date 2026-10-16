package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the agentic systems of the application: an agent whose class no other agent lists as a sub-agent is the
 * root of a system; every other agent appears only nested in the systems that use it.
 */
public class RootSystemDetector {

    /**
     * @param descriptors every agent method, as produced by {@link AgenticAnnotationScanner}
     * @return one tree per root system, sorted by root id
     */
    public List<AgentTree> detect(List<AgentDescriptor> descriptors) {
        List<AgentDescriptor> sorted = descriptors.stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
        // sub-agents are referenced by class; a class with several agent methods resolves to the first by id
        Map<String, AgentDescriptor> byClass = new LinkedHashMap<>();
        Set<String> referenced = new HashSet<>();
        for (AgentDescriptor descriptor : sorted) {
            byClass.putIfAbsent(descriptor.declaringClass(), descriptor);
            referenced.addAll(descriptor.subAgents());
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
            children.add(tree(subAgent, byClass.get(subAgent), byClass, ancestors));
        }
        ancestors.remove(agentClass);
        return new AgentTree(agentClass, descriptor, children);
    }
}
