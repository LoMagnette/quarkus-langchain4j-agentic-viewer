package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.List;
import java.util.Map;

import io.quarkus.runtime.annotations.RecordableConstructor;

/**
 * Planner behind a {@link NodeKind#PLANNER} node.
 *
 * @param plannerClass fully qualified class name of the planner, null when only known at runtime (a declarative
 *        {@code @PlannerSupplier} creates it)
 * @param patternType recognized pattern, {@link PatternType#CUSTOM} otherwise
 * @param extras pattern-specific information (e.g. the goal key of a GOAP planner); {@code supplier} is the
 *        {@code Class#method} of the {@code @PlannerSupplier} of a declarative planner
 * @param desires the desires of a {@link PatternType#BDI} planner in declaration order, when its supplier declares
 *        them literally; null when unknown (another pattern, a builder system: the planner keeps them private)
 */
public record PlannerInfo(String plannerClass, PatternType patternType, Map<String, String> extras,
        List<PlannerDesire> desires) {

    /** The one the build-time recorder uses (a record with several constructors needs it told). */
    @RecordableConstructor
    public PlannerInfo {
        extras = extras == null ? Map.of() : Map.copyOf(extras);
        desires = desires == null ? null : List.copyOf(desires);
    }

    public PlannerInfo(String plannerClass, PatternType patternType, Map<String, String> extras) {
        this(plannerClass, patternType, extras, null);
    }

    public PlannerInfo withDesires(List<PlannerDesire> desires) {
        return new PlannerInfo(plannerClass, patternType, extras, desires);
    }
}
