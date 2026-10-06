package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

/**
 * Represents data flow between agents via @V bindings and outputKey.
 *
 * @param sourceAgentId the agent producing the data
 * @param targetAgentId the agent consuming the data
 * @param variableName the variable name from @V("name")
 * @param outputKey the output key from the source agent
 */
public record DataFlow(
        String sourceAgentId,
        String targetAgentId,
        String variableName,
        String outputKey) {
}
