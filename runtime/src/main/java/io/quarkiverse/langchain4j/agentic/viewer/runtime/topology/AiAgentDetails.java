package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.List;

import io.quarkus.runtime.annotations.RecordableConstructor;

/**
 * What is known before running about the LLM side of an {@link NodeKind#AI_AGENT} node.
 *
 * @param systemMessage {@code @SystemMessage} template of the method, else of its class; null when none
 * @param userMessage {@code @UserMessage} template of the method, null when none
 * @param modelName {@code @ModelName} of the method, null for the default model
 * @param tools {@code @Tool} methods of the {@code @ToolBox} classes, in {@code @ToolBox} order; within a class by
 *        method name, inherited tools after the class's own
 * @param runtimeToolSources tools only known when the application runs: {@code Class#method} of a
 *        {@code @ToolsSupplier} or {@code @ToolProviderSupplier} method, {@code mcp:<client>} for each
 *        {@code @McpToolBox} client
 * @param chatModelSupplier {@code Class#method} of the {@code @ChatModelSupplier} method giving the agent its model,
 *        null when none
 * @param streamingChatModelSupplier {@code Class#method} of the {@code @StreamingChatModelSupplier} method, null when
 *        none
 * @param skills the values of the method's {@code @Skills} (quarkus-langchain4j), empty for one without values; null
 *        when the method has none
 */
public record AiAgentDetails(
        String systemMessage,
        String userMessage,
        String modelName,
        List<ToolInfo> tools,
        List<String> runtimeToolSources,
        String chatModelSupplier,
        String streamingChatModelSupplier,
        List<String> skills) {

    /** The one the build-time recorder uses (a record with several constructors needs it told). */
    @RecordableConstructor
    public AiAgentDetails {
        tools = tools == null ? List.of() : List.copyOf(tools);
        runtimeToolSources = runtimeToolSources == null ? List.of() : List.copyOf(runtimeToolSources);
        skills = skills == null ? null : List.copyOf(skills);
    }

    /** Without model suppliers nor skills. */
    public AiAgentDetails(String systemMessage, String userMessage, String modelName, List<ToolInfo> tools,
            List<String> runtimeToolSources) {
        this(systemMessage, userMessage, modelName, tools, runtimeToolSources, null, null, null);
    }
}
