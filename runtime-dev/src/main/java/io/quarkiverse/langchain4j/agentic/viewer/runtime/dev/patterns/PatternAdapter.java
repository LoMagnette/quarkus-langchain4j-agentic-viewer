package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import java.util.Map;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * What the Dev UI knows about a planner of langchain4j-agentic-patterns (F5). Implementations reference the patterns
 * classes: they are registered as beans only when the module is on the class path (AgenticViewerProcessor), and nothing
 * else refers to them, so they are never loaded without it. This interface does not refer to the module.
 */
public interface PatternAdapter {

    /**
     * @return the pattern of the planners this adapter handles
     */
    PatternType patternType();

    /**
     * @return whether the planner is one this adapter handles
     */
    boolean handles(Planner planner);

    /**
     * Pattern-specific information on a decision of the planner (GOAP path, P2P activations, blackboard candidates),
     * added to the extras of the PlannerDecision event.
     *
     * @param planner the planner, one this adapter {@link #handles}
     * @param action what it decided
     * @param scope the AgenticScope of the run
     */
    default Map<String, Object> decisionExtras(Planner planner, Action action, AgenticScope scope) {
        return Map.of();
    }
}
