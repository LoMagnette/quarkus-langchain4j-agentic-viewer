package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.List;

/**
 * A desire of a BDI planner (langchain4j-agentic-patterns {@code Desire}), as declared in its {@code @PlannerSupplier}.
 *
 * @param name name of the desire
 * @param priority priority (the highest achievable, unsatisfied desire is pursued), null when not a literal
 * @param achievableKey the desire is achievable once this AgenticScope key is present; null for a predicate or unknown
 * @param satisfiedKey the desire is satisfied once this AgenticScope key is present; null for a predicate or unknown
 * @param agents node ids of the planner's children it calls in order (its intention)
 */
public record PlannerDesire(String name, Integer priority, String achievableKey, String satisfiedKey, List<String> agents) {

    public PlannerDesire {
        agents = agents == null ? List.of() : List.copyOf(agents);
    }
}
