package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.List;

/**
 * What the build knows about the LLM side of an AI agent.
 *
 * @param systemMessage {@code @SystemMessage} template of the method, else of its class; null when none
 * @param userMessage {@code @UserMessage} template of the method, null when none
 * @param modelName {@code @ModelName} of the method, null for the default model
 * @param tools {@code @Tool} methods of the {@code @ToolBox} classes, in {@code @ToolBox} order; within a class by
 *        method name, inherited tools after the class's own
 * @param runtimeToolSources tools only known when the application runs: {@code Class#method} of a
 *        {@code @ToolsSupplier} or {@code @ToolProviderSupplier} method, {@code mcp:<client>} for each
 *        {@code @McpToolBox} client
 */
public record AiAgentDetails(
        String systemMessage,
        String userMessage,
        String modelName,
        List<ToolDescriptor> tools,
        List<String> runtimeToolSources) {

    public AiAgentDetails {
        tools = tools == null ? List.of() : List.copyOf(tools);
        runtimeToolSources = runtimeToolSources == null ? List.of() : List.copyOf(runtimeToolSources);
    }
}
