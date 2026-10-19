package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.patterns.bdi.BDIPlanner;
import dev.langchain4j.agentic.planner.Planner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * BDI (belief-desire-intention) planners of langchain4j-agentic-patterns. Registered only when the module is present.
 * <p>
 * The planner pursues the achievable, unsatisfied desire of highest priority by calling its agents in order (its
 * intention), switching when a higher desire becomes achievable, until no desire is left to pursue. Its desires and
 * the current one are private: the adapter only tells the invocations so far (public {@code executionState}). The
 * Dev UI tells the desire of each step and the satisfied desires from the desires its supplier declares literally
 * (PlannerInfo.desires, build time).
 */
@ApplicationScoped
public class BdiAdapter implements PatternAdapter {

    /** Key of the invocation counter in the planner's executionState. */
    static final String INVOCATION_COUNTER = "invocationCounter";

    @Override
    public PatternType patternType() {
        return PatternType.BDI;
    }

    @Override
    public boolean handles(Planner planner) {
        return planner instanceof BDIPlanner;
    }

    @Override
    public Insight insight(Decision decision) {
        Object counter = decision.planner().executionState().get(INVOCATION_COUNTER);
        return counter == null ? Insight.NONE : new Insight(Map.of("invocations", counter), Map.of());
    }
}
