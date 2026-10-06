package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

/**
 * Types of agents that can be detected in the codebase.
 */
public enum AgentType {
    /**
     * A basic agent annotated with @Agent
     */
    AGENT,

    /**
     * A sequence workflow agent annotated with @SequenceAgent
     */
    SEQUENCE,

    /**
     * A parallel workflow agent annotated with @ParallelAgent
     */
    PARALLEL,

    /**
     * A conditional workflow agent annotated with @ConditionalAgent
     */
    CONDITIONAL,

    /**
     * A loop workflow agent annotated with @LoopAgent
     */
    LOOP,

    /**
     * An AI service registered with @RegisterAiService
     */
    AI_SERVICE,

    /**
     * An output composition method annotated with @Output
     */
    OUTPUT,

    /**
     * A remote A2A (Agent-to-Agent) client agent
     */
    A2A
}
