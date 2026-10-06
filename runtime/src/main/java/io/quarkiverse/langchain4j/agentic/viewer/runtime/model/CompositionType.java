package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

/**
 * Types of workflow compositions.
 */
public enum CompositionType {
    /**
     * Sequential execution of child agents
     */
    SEQUENCE,

    /**
     * Parallel execution of child agents
     */
    PARALLEL,

    /**
     * Conditional execution based on a condition
     */
    CONDITIONAL,

    /**
     * Loop execution until condition is met
     */
    LOOP
}
