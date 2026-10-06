package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.List;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;

/**
 * Build-time description of one agent method found in the index.
 *
 * @param id unique id: declaring class + {@code #} + method name
 * @param declaringClass fully qualified name of the declaring interface or class
 * @param methodName agent method name
 * @param kind node kind: AI_AGENT for an abstract {@code @Agent} method, NON_AI_AGENT for a concrete one, otherwise
 *        the workflow kind of the annotation
 * @param name agent name (the annotation's name, or the method name)
 * @param description annotation description, null when none
 * @param outputKey AgenticScope key the agent writes, null when none
 * @param inputKeys AgenticScope keys read through the method parameters, in order
 * @param subAgents fully qualified class names of the sub-agents, in declaration order
 * @param itemsProvider scope key of the collection a parallel mapper iterates on, null otherwise
 */
public record AgentDescriptor(
        String id,
        String declaringClass,
        String methodName,
        NodeKind kind,
        String name,
        String description,
        String outputKey,
        List<String> inputKeys,
        List<String> subAgents,
        String itemsProvider) {

    public AgentDescriptor {
        inputKeys = inputKeys == null ? List.of() : List.copyOf(inputKeys);
        subAgents = subAgents == null ? List.of() : List.copyOf(subAgents);
    }
}
