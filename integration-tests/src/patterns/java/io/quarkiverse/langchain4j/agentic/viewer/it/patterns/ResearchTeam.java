package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastUserMessage;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.p2p.P2PPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Peer-to-peer (P2P): each agent runs as soon as its inputs are in the scope, and again when they change.
 * The critic and the refiner form a cycle that ends through the exit condition, when the critique is SOLID
 * (after 7 invocations, below the maximum of 10).
 */
public interface ResearchTeam {

    int MAX_INVOCATIONS = 10;

    @PlannerAgent(description = "A research team refining a hypothesis until the critic finds it solid", outputKey = "hypothesis", subAgents = {
            LiteratureSearcher.class, HypothesisFormulator.class, Critic.class, Refiner.class })
    String research(@V("topic") String topic);

    @PlannerSupplier
    static Planner planner() {
        return new P2PPlanner(MAX_INVOCATIONS, scope -> scope.readState("critique", "").startsWith("SOLID"));
    }

    interface LiteratureSearcher {
        @UserMessage("Find papers about {{topic}}")
        @Agent(description = "Finds papers about the topic", outputKey = "papers")
        String searchPapers(@V("topic") String topic);
    }

    interface HypothesisFormulator {
        @UserMessage("Formulate a hypothesis about {{topic}} from these papers: {{papers}}")
        @Agent(description = "Formulates a first hypothesis", outputKey = "hypothesis")
        String formulate(@V("topic") String topic, @V("papers") String papers);
    }

    interface Critic {
        @UserMessage("Criticize this hypothesis, starting with SOLID or WEAK: {{hypothesis}}")
        @Agent(description = "Criticizes the hypothesis", outputKey = "critique")
        String criticize(@V("hypothesis") String hypothesis);
    }

    interface Refiner {
        @UserMessage("Refine the hypothesis according to this critique: {{critique}}")
        @Agent(description = "Refines the hypothesis", outputKey = "hypothesis")
        String refine(@V("critique") String critique);
    }

    /**
     * Hypotheses are versioned; the critic finds version 3 solid.
     */
    @ApplicationScoped
    class Script implements ChatModelScript {

        private static final Pattern VERSION = Pattern.compile("Hypothesis v(\\d+) about (.+?)\\)?$");

        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Find papers about")).reply("3 papers")
                    .when(userMessageContains("Formulate a hypothesis about"))
                    .reply(request -> "Hypothesis v1 about "
                            + userMessageAfter(request, "hypothesis about").replaceFirst(" from these papers: .*", ""))
                    .when(userMessageContains("Criticize this hypothesis"))
                    .reply(request -> {
                        String hypothesis = userMessageAfter(request, "SOLID or WEAK:");
                        return (hypothesis.startsWith("Hypothesis v3") ? "SOLID: well supported (" : "WEAK: needs evidence (")
                                + hypothesis + ")";
                    })
                    .when(userMessageContains("Refine the hypothesis according to this critique:"))
                    .reply(request -> {
                        Matcher matcher = VERSION.matcher(lastUserMessage(request).lines().findFirst().orElse(""));
                        if (!matcher.find()) {
                            throw new IllegalStateException("No hypothesis in " + lastUserMessage(request));
                        }
                        return "Hypothesis v" + (Integer.parseInt(matcher.group(1)) + 1) + " about " + matcher.group(2);
                    });
        }
    }
}
