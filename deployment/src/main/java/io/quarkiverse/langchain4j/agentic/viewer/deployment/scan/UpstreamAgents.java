package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import io.quarkiverse.langchain4j.agentic.deployment.DetectedAiAgentAsMapBuildItem;
import io.quarkiverse.langchain4j.agentic.deployment.DetectedAiAgentBuildItem;
import io.quarkiverse.langchain4j.agentic.deployment.DetectedNonAiAgentAsMapBuildItem;

/**
 * The agents quarkus-langchain4j-agentic detected, from its build items: the identity of the agents it builds, and
 * what it resolved for each AI agent interface. Its rule: an interface with a method carrying one of its agent
 * annotations is an AI agent, with its sub-interfaces (they list the inherited methods); a class with one is a non-AI
 * agent. Nothing about the structure of the systems (sub-agents, keys, conditions...): the scanner reads that.
 *
 * @param agentMethods the agent methods of each agent class (interfaces and classes), by class name
 * @param aiAgents what quarkus-langchain4j resolved for each AI agent interface, by class name
 */
public record UpstreamAgents(Map<String, List<MethodInfo>> agentMethods, Map<String, AiAgent> aiAgents) {

    /**
     * @param modelName the {@code @ModelName} of its first {@code @Agent} method, null when none
     * @param chatModelSupplier {@code Class#method} of its static {@code @ChatModelSupplier} method, null when none
     * @param toolBoxMethods its methods with {@code @ToolBox}
     * @param mcpToolBoxMethods its methods with {@code @McpToolBox}
     * @param skillsMethods its methods with {@code @Skills}
     */
    public record AiAgent(String modelName, String chatModelSupplier, List<MethodInfo> toolBoxMethods,
            List<MethodInfo> mcpToolBoxMethods, List<MethodInfo> skillsMethods) {

        public AiAgent {
            toolBoxMethods = toolBoxMethods == null ? List.of() : List.copyOf(toolBoxMethods);
            mcpToolBoxMethods = mcpToolBoxMethods == null ? List.of() : List.copyOf(mcpToolBoxMethods);
            skillsMethods = skillsMethods == null ? List.of() : List.copyOf(skillsMethods);
        }

        public AiAgent(String modelName, String chatModelSupplier) {
            this(modelName, chatModelSupplier, List.of(), List.of(), List.of());
        }
    }

    public UpstreamAgents {
        agentMethods = Map.copyOf(agentMethods);
        aiAgents = Map.copyOf(aiAgents);
    }

    public static UpstreamAgents of(DetectedAiAgentAsMapBuildItem aiAgents, DetectedNonAiAgentAsMapBuildItem nonAiAgents,
            Collection<DetectedAiAgentBuildItem> detected) {
        Map<String, List<MethodInfo>> methods = new TreeMap<>();
        add(methods, aiAgents == null ? Map.of() : aiAgents.getIfaceToAgentMethodsMap());
        add(methods, nonAiAgents == null ? Map.of() : nonAiAgents.getClassToNonAiAgentMethodsMap());
        Map<String, AiAgent> ai = new TreeMap<>();
        for (DetectedAiAgentBuildItem agent : detected) {
            MethodInfo supplier = agent.getChatModelSupplier();
            ai.put(agent.getIface().name().toString(), new AiAgent(agent.getModelName(),
                    supplier == null ? null : supplier.declaringClass().name() + "#" + supplier.name(),
                    agent.getToolBoxMethods(), agent.getMcpToolBoxMethods(), agent.getSkillsMethods()));
        }
        return new UpstreamAgents(methods, ai);
    }

    private static void add(Map<String, List<MethodInfo>> methods, Map<DotName, List<MethodInfo>> detected) {
        detected.forEach((type, agentMethods) -> methods.merge(type.toString(), List.copyOf(agentMethods),
                (a, b) -> Stream.concat(a.stream(), b.stream()).distinct().toList()));
    }
}
