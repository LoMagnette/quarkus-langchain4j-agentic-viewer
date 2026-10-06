package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

/**
 * Represents a parameter of a tool method.
 *
 * @param name the parameter name
 * @param type the parameter type
 */
public record ToolParameter(String name, String type) {
}
