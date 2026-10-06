package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

import java.util.List;

/**
 * Represents an agent detected in the codebase.
 *
 * @param id unique identifier for this agent
 * @param name the agent name
 * @param type the type of agent (AGENT, SEQUENCE, PARALLEL, etc.)
 * @param description the agent description
 * @param systemMessage the system message content (may be truncated)
 * @param userMessage the user message template (may be truncated)
 * @param outputKey the output key for data flow
 * @param toolIds list of tool IDs this agent uses
 * @param className the fully qualified class name
 * @param methodName the method name (for method-level agents)
 * @param isRemote whether this is a remote A2A agent
 * @param remoteUrl the remote URL for A2A agents
 * @param inputParameters list of input parameters for this agent
 */
public record AgentNode(
        String id,
        String name,
        AgentType type,
        String description,
        String systemMessage,
        String userMessage,
        String outputKey,
        List<String> toolIds,
        String className,
        String methodName,
        boolean isRemote,
        String remoteUrl,
        List<AgentParameter> inputParameters) {
}
