package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.List;

/**
 * A node of an agentic system topology: an agent or a workflow, with its sub-agents as children.
 *
 * @param id identifier, stable across builds of the same code; runtime events are correlated with it
 * @param name agent name (the method name unless the annotation sets one)
 * @param kind node kind
 * @param description agent description, null when none
 * @param inputKeys AgenticScope keys the agent reads
 * @param outputKey AgenticScope key the agent writes, null when none
 * @param optional whether the agent is optional
 * @param async whether the agent runs asynchronously
 * @param children sub-agents, in declaration order
 * @param guard exit condition of a loop, or activation condition of a conditional branch; null otherwise
 * @param plannerInfo planner of a {@link NodeKind#PLANNER} node, null otherwise
 */
public record AgentNode(
        String id,
        String name,
        NodeKind kind,
        String description,
        List<String> inputKeys,
        String outputKey,
        boolean optional,
        boolean async,
        List<AgentNode> children,
        Guard guard,
        PlannerInfo plannerInfo) {

    public AgentNode {
        inputKeys = inputKeys == null ? List.of() : List.copyOf(inputKeys);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
