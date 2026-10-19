package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.patterns.voting.VotingStrategy;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Voting: every reviewer votes on the change at once; the majority of their votes is the decision.
 */
public interface ReviewPanel {

    @PlannerAgent(description = "Reviewers vote on a change, the majority decides", outputKey = "decision", subAgents = {
            StyleReviewer.class, SecurityReviewer.class, PerformanceReviewer.class })
    String review(@V("change") String change);

    @PlannerSupplier
    static Planner planner() {
        return new VotingPlanner(VotingStrategy.majority());
    }

    interface StyleReviewer {
        @UserMessage("Vote APPROVE or REJECT on the style of: {{change}}")
        @Agent(description = "Votes on the style", outputKey = "styleVote")
        String voteOnStyle(@V("change") String change);
    }

    interface SecurityReviewer {
        @UserMessage("Vote APPROVE or REJECT on the security of: {{change}}")
        @Agent(description = "Votes on the security", outputKey = "securityVote")
        String voteOnSecurity(@V("change") String change);
    }

    interface PerformanceReviewer {
        @UserMessage("Vote APPROVE or REJECT on the performance of: {{change}}")
        @Agent(description = "Votes on the performance", outputKey = "performanceVote")
        String voteOnPerformance(@V("change") String change);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("on the style of:")).reply("APPROVE")
                    .when(userMessageContains("on the security of:")).reply("REJECT")
                    .when(userMessageContains("on the performance of:")).reply("APPROVE");
        }
    }
}
