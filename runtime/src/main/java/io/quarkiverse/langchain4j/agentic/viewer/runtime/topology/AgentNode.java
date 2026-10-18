package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.List;

import io.quarkus.runtime.annotations.RecordableConstructor;

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
 * @param aiDetails prompts, model and tools of an {@link NodeKind#AI_AGENT} node, null otherwise or when unknown
 * @param remoteDetails server or MCP tool of an A2A or MCP client agent, null otherwise
 * @param code the application code behind the node (agent method, @Output, conditions, prompt resources...), for the
 *        Dev UI to open in the IDE
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
        PlannerInfo plannerInfo,
        AiAgentDetails aiDetails,
        RemoteAgentDetails remoteDetails,
        List<CodeRef> code) {

    /** Without code references. */
    public AgentNode(String id, String name, NodeKind kind, String description, List<String> inputKeys, String outputKey,
            boolean optional, boolean async, List<AgentNode> children, Guard guard, PlannerInfo plannerInfo,
            AiAgentDetails aiDetails, RemoteAgentDetails remoteDetails) {
        this(id, name, kind, description, inputKeys, outputKey, optional, async, children, guard, plannerInfo, aiDetails,
                remoteDetails, List.of());
    }


    /** The one the build-time recorder uses (a record with several constructors needs it told). */
    @RecordableConstructor
    public AgentNode {
        inputKeys = inputKeys == null ? List.of() : List.copyOf(inputKeys);
        children = children == null ? List.of() : List.copyOf(children);
        code = code == null ? List.of() : List.copyOf(code);
    }
}
