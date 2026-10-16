package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.planner.Planner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * Blackboard planners of langchain4j-agentic-patterns. Registered only when the module is present.
 */
@ApplicationScoped
public class BlackboardAdapter implements PatternAdapter {

    @Override
    public PatternType patternType() {
        return PatternType.BLACKBOARD;
    }

    @Override
    public boolean handles(Planner planner) {
        return planner instanceof BlackboardPlanner;
    }
}
