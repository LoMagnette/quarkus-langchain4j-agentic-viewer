package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.planner.Planner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * Debate planners of langchain4j-agentic-patterns. Registered only when the module is present.
 * <p>
 * The planner takes its last sub-agent as the judge and the others as debaters: each round calls every debater, with
 * the arguments so far in the scope ({@code debateContext}), until their last messages converge or the maximum of
 * rounds is reached; then the judge decides. The adapter tells the round and the debaters' messages of the last round
 * (public {@code executionState}); the Dev UI tells the judge from the agent called. The maximum of rounds and the
 * convergence strategy are private: why the judge was called (convergence or maximum) is not told.
 */
@ApplicationScoped
public class DebateAdapter implements PatternAdapter {

    /** Keys of the planner's executionState. */
    static final String CURRENT_ROUND = "currentRound";
    static final String LAST_MESSAGES = "lastDebatersMessages";

    @Override
    public PatternType patternType() {
        return PatternType.DEBATE;
    }

    @Override
    public boolean handles(Planner planner) {
        return planner instanceof DebatePlanner;
    }

    @Override
    public Insight insight(Decision decision) {
        Map<String, Object> state = decision.planner().executionState();
        Map<String, Object> extras = new HashMap<>();
        if (state.get(CURRENT_ROUND) instanceof Integer round) {
            extras.put("round", round);
        }
        if (state.get(LAST_MESSAGES) instanceof Map<?, ?> messages) {
            extras.put("messages", messages.size());
        }
        return new Insight(extras, Map.of());
    }
}
