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
    HUMAN_IN_THE_LOOP
}
