package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.agentic.planner.Planner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * Goal-oriented (GOAP) planners of langchain4j-agentic-patterns. Registered only when the module is present.
 */
@ApplicationScoped
public class GoapAdapter implements PatternAdapter {

    @Override
    public PatternType patternType() {
        return PatternType.GOAP;
    }

    @Override
    public boolean handles(Planner planner) {
        return planner instanceof GoalOrientedPlanner;
    }
}
