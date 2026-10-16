package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * Kind of a node in an agentic system topology.
 */
public enum NodeKind {
    AI_AGENT,
    NON_AI_AGENT,
    SEQUENCE,
    PARALLEL,
    PARALLEL_MAPPER,
    LOOP,
    CONDITIONAL,
    SUPERVISOR,
    PLANNER,
    HUMAN_IN_THE_LOOP,
    /** A remote agent called through the A2A protocol ({@code @A2AClientAgent}). */
    A2A_CLIENT_AGENT,
    /** An agent calling one tool of an MCP server ({@code @McpClientAgent}). */
    MCP_CLIENT_AGENT
}
