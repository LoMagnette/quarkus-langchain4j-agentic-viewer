package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
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
 * @param plannerSupplier {@code Class#method} of the {@code @PlannerSupplier} method of a planner agent, null otherwise
 * @param loopGuard exit condition of a loop (method name, null label when the loop has none), null otherwise
 * @param branchGuards activation condition of each conditional branch, keyed by sub-agent class name; a sub-agent
 *        without one is absent (langchain4j then leaves it out of the conditional)
 * @param outputMethod {@code Class#method} of the {@code @Output} method of a workflow, null when none
 * @param optional whether the agent is optional ({@code @Agent(optional)})
 * @param async whether the agent runs asynchronously ({@code @Agent(async)}, {@code @HumanInTheLoop(async)})
 * @param aiDetails prompts, model and tools of an AI_AGENT, null otherwise
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
        String itemsProvider,
        String plannerSupplier,
        Guard loopGuard,
        Map<String, Guard> branchGuards,
        String outputMethod,
        boolean optional,
        boolean async,
        AiAgentDetails aiDetails) {

    public AgentDescriptor {
        inputKeys = inputKeys == null ? List.of() : List.copyOf(inputKeys);
        subAgents = subAgents == null ? List.of() : List.copyOf(subAgents);
        // keeps the sub-agent order of the scanner
        branchGuards = branchGuards == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(branchGuards));
    }
}
