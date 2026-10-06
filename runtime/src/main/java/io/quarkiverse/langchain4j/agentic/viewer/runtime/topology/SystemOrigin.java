package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * How an agentic system is defined.
 */
public enum SystemOrigin {
    /** Annotated interfaces ({@code @SequenceAgent}, {@code @PlannerAgent}...), known at build time. */
    DECLARATIVE,
    /** {@code AgenticServices.*Builder().build()} in application code, known at runtime only. */
    BUILDER,
    /** A builder workflow using declarative agents, or the reverse. */
    MIXED
}
