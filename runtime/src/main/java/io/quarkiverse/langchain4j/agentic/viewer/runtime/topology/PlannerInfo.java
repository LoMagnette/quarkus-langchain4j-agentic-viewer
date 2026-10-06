package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.Map;

/**
 * Planner behind a {@link NodeKind#PLANNER} node.
 *
 * @param plannerClass fully qualified class name of the planner
 * @param patternType recognized pattern, {@link PatternType#CUSTOM} otherwise
 * @param extras pattern-specific information (e.g. the goal key of a GOAP planner)
 */
public record PlannerInfo(String plannerClass, PatternType patternType, Map<String, String> extras) {

    public PlannerInfo {
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }
}
