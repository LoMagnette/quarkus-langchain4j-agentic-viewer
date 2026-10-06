package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * Planner implementation behind a {@link NodeKind#PLANNER} node.
 */
public enum PatternType {
    /** {@code GoalOrientedPlanner} from langchain4j-agentic-patterns. */
    GOAP,
    /** {@code P2PPlanner} from langchain4j-agentic-patterns. */
    P2P,
    /** {@code BlackboardPlanner} from langchain4j-agentic-patterns. */
    BLACKBOARD,
    /** Any other planner. */
    CUSTOM
}
