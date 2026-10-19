package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentInvocation;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * Voting planners of langchain4j-agentic-patterns. Registered only when the module is present.
 * <p>
 * The planner calls all its sub-agents (voters) at once, then aggregates their outputs with its voting strategy into
 * its result. The adapter tells the number of voters and, when done, each voter's vote: the output of its last
 * invocation in the scope (public). The strategy is private: not told.
 */
@ApplicationScoped
public class VotingAdapter implements PatternAdapter {

    @Override
    public PatternType patternType() {
        return PatternType.VOTING;
    }

    @Override
    public boolean handles(Planner planner) {
        return planner instanceof VotingPlanner;
    }

    @Override
    public Insight insight(Decision decision) {
        Map<String, Object> extras = new HashMap<>();
        extras.put("voters", decision.subagents().size());
        if (decision.action().isDone() && decision.scope() != null) {
            // in voter order; a voter that did not vote (yet) has no entry
            List<Object> votes = new ArrayList<>();
            List<AgentInstance> voted = new ArrayList<>();
            for (AgentInstance voter : decision.subagents()) {
                List<AgentInvocation> invocations = decision.scope().agentInvocations(voter.name());
                if (invocations != null && !invocations.isEmpty()) {
                    votes.add(invocations.get(invocations.size() - 1).output());
                    voted.add(voter);
                }
            }
            extras.put("votes", votes);
            return new Insight(extras, Map.of("voted", voted));
        }
        return new Insight(extras, Map.of());
    }
}
