package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

import java.util.List;

/**
 * Represents a tool detected in the codebase.
 *
 * @param id unique identifier for this tool
 * @param name the tool name (method name)
 * @param description the tool description from @Tool annotation
 * @param className the fully qualified class name containing the tool
 * @param methodName the method name
 * @param parameters the tool parameters
 * @param returnType the return type of the tool method
 */
public record ToolNode(
        String id,
        String name,
        String description,
        String className,
        String methodName,
        List<ToolParameter> parameters,
        String returnType) {
}
