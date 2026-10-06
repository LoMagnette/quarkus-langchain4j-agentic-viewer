package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

/**
 * Represents an input parameter for an agent method.
 *
 * @param name the parameter name (from @V annotation or parameter name)
 * @param type the parameter type
 * @param isMemoryId whether this parameter is annotated with @MemoryId
 */
public record AgentParameter(
        String name,
        String type,
        boolean isMemoryId) {
}
