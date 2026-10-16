package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.List;

/**
 * An agent with its sub-agents expanded, as they are nested in an agentic system.
 *
 * @param agentClass fully qualified name of the agent class (the sub-agent reference)
 * @param descriptor the agent method of that class, null when the class has none in the index
 * @param children sub-agents in declaration order; empty for a leaf, for an unresolved class, or where a cycle back
 *        to an ancestor was cut
 */
public record AgentTree(String agentClass, AgentDescriptor descriptor, List<AgentTree> children) {

    public AgentTree {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
