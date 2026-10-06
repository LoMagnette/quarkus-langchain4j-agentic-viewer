package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Custom planner: drafts, then reviews, and drafts again until the reviewer approves (at most 3 drafts).
 */
public interface DraftReviewer {

    @PlannerAgent(description = "Drafts a text and reworks it until the reviewer approves", outputKey = "draft", subAgents = {
            Drafter.class, Reviewer.class })
    String write(@V("topic") String topic);

    @PlannerSupplier
    static Planner planner() {
        return new DraftReviewPlanner();
    }

    interface Drafter {
        @UserMessage("Write a draft about {{topic}}. Reviewer feedback: {{verdict}}")
        @Agent(description = "Writes a draft, taking the reviewer feedback into account", outputKey = "draft")
        String writeDraft(@V("topic") String topic, @V("verdict") String verdict);
    }

    interface Reviewer {
        @UserMessage("Review this draft and answer APPROVED or REJECTED with a reason: {{draft}}")
        @Agent(description = "Approves or rejects a draft", outputKey = "verdict")
        String review(@V("draft") String draft);
    }

    class DraftReviewPlanner implements Planner {

        static final int MAX_DRAFTS = 3;

        private AgentInstance drafter;
        private AgentInstance reviewer;
        private int drafts;

        @Override
        public void init(InitPlanningContext context) {
            for (AgentInstance agent : context.subagents()) {
                switch (agent.name()) {
                    case "writeDraft" -> drafter = agent;
                    case "review" -> reviewer = agent;
                    default -> throw new IllegalStateException("Unexpected sub-agent " + agent.name());
                }
            }
        }

        @Override
        public Action firstAction(PlanningContext context) {
            context.agenticScope().writeState("verdict", "none yet");
            drafts = 1;
            return call(drafter);
        }

        @Override
        public Action nextAction(PlanningContext context) {
            if (context.previousAgentInvocation().agentName().equals(drafter.name())) {
                return call(reviewer);
            }
            String verdict = context.agenticScope().readState("verdict", "");
            if (verdict.startsWith("APPROVED") || drafts >= MAX_DRAFTS) {
                return done();
            }
            drafts++;
            return call(drafter);
        }
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Write a draft about").and(userMessageContains("feedback: none yet")))
                    .reply(request -> "A rough draft about " + topic(userMessageAfter(request, "about")))
                    .when(userMessageContains("Write a draft about").and(userMessageContains("feedback: REJECTED")))
                    .reply(request -> "A polished draft about " + topic(userMessageAfter(request, "about")))
                    .when(userMessageContains("Review this draft").and(userMessageContains("A rough draft")))
                    .reply("REJECTED: too rough")
                    .when(userMessageContains("Review this draft").and(userMessageContains("A polished draft")))
                    .reply("APPROVED");
        }

        private static String topic(String textAfterAbout) {
            int end = textAfterAbout.indexOf(". Reviewer feedback:");
            return end < 0 ? textAfterAbout : textAfterAbout.substring(0, end);
        }
    }
}
