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
    /** {@code BDIPlanner} (belief-desire-intention) from langchain4j-agentic-patterns. */
    BDI,
    /** {@code DebatePlanner} from langchain4j-agentic-patterns: the last sub-agent is the judge. */
    DEBATE,
    /** {@code VotingPlanner} from langchain4j-agentic-patterns. */
    VOTING,
    /** Any other planner. */
    CUSTOM
}
