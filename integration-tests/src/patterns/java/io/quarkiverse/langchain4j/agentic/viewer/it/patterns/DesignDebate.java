package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.debate.ConvergenceStrategy;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Debate: the debaters argue in rounds until they agree or the maximum of rounds is reached; the last sub-agent, the
 * judge, then decides. Here they never agree: two rounds, then the judge. (The planner writes the arguments so far to
 * {@code debateContext}; quarkus-langchain4j rejects at build time an agent parameter that no agent writes, so these
 * agents do not read it.)
 */
public interface DesignDebate {

    @PlannerAgent(description = "Debates a design proposal, then a judge decides", outputKey = "verdict", subAgents = {
            Advocate.class, Critic.class, Judge.class })
    String debate(@V("proposal") String proposal);

    @PlannerSupplier
    static Planner planner() {
        return new DebatePlanner(2, ConvergenceStrategy.unanimous());
    }

    interface Advocate {
        @UserMessage("Argue for the proposal {{proposal}}")
        @Agent(description = "Argues for the proposal", outputKey = "advocacy")
        String argueFor(@V("proposal") String proposal);
    }

    interface Critic {
        @UserMessage("Argue against the proposal {{proposal}}")
        @Agent(description = "Argues against the proposal", outputKey = "criticism")
        String argueAgainst(@V("proposal") String proposal);
    }

    interface Judge {
        @UserMessage("Decide on the proposal {{proposal}}")
        @Agent(description = "Decides after the debate", outputKey = "verdict")
        String decide(@V("proposal") String proposal);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Argue for the proposal")).reply("Adopt it: one queue is simpler")
                    .when(userMessageContains("Argue against the proposal")).reply("Reject it: one queue is a bottleneck")
                    .when(userMessageContains("Decide on the proposal")).reply("Adopt one queue per region");
        }
    }
}
