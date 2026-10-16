package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * A {@code @Tool} method reachable from an AI agent.
 *
 * @param name tool name seen by the model: {@code @Tool(name)}, or the method name
 * @param declaringClass fully qualified name of the tool class
 * @param methodName tool method name
 * @param description {@code @Tool} value lines joined with a new line, null when none
 */
public record ToolInfo(String name, String declaringClass, String methodName, String description) {
}
