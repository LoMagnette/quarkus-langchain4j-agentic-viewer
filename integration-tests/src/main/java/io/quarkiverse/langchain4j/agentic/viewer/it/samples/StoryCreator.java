package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastUserMessage;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Loop nested in a sequence: a first draft, then a style review loop that exits after 3 iterations,
 * when the style score reaches 0.8.
 */
public interface StoryCreator {

    @SequenceAgent(description = "Drafts a story, then polishes its style", outputKey = "story", subAgents = {
            Drafter.class, StyleReviewLoop.class })
    String create(@V("topic") String topic);

    interface Drafter {
        @UserMessage("Draft a story about {{topic}}")
        @Agent(description = "Writes the first draft of the story", outputKey = "story", compensateOnError = true)
        String draft(@V("topic") String topic);
    }

    interface StyleReviewLoop {
        @LoopAgent(description = "Polishes the story until its style score is at least 0.8", outputKey = "story", maxIterations = 5, subAgents = {
                StyleScorer.class, StyleEditor.class })
        String polish(@V("story") String story);

        @ExitCondition(testExitAtLoopEnd = true, description = "style score is at least 0.8")
        static boolean goodEnough(@V("score") double score) {
            return score >= 0.8;
        }
    }

    interface StyleScorer {
        @UserMessage("Score the style of this story from 0.0 to 1.0: {{story}}")
        @Agent(description = "Scores the style of the story", outputKey = "score")
        double score(@V("story") String story);
    }

    interface StyleEditor {
        @UserMessage("Improve the style of this story: {{story}}")
        @Agent(description = "Rewrites the story with a better style", outputKey = "story")
        String edit(@V("story") String story);
    }

    /**
     * Drafts are numbered: each edit increments the number and the score grows with it,
     * so the loop deterministically ends after 3 iterations.
     */
    @ApplicationScoped
    class Script implements ChatModelScript {

        private static final Pattern DRAFT_NUMBER = Pattern.compile("\\(draft (\\d+)\\)");

        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Draft a story about"))
                    .reply(request -> "A story about " + userMessageAfter(request, "about") + " (draft 1)")
                    .when(userMessageContains("Score the style of this story"))
                    .reply(request -> switch (draftNumber(request)) {
                        case 1 -> "0.3";
                        case 2 -> "0.6";
                        default -> "0.9";
                    })
                    .when(userMessageContains("Improve the style of this story:"))
                    .reply(request -> DRAFT_NUMBER.matcher(userMessageAfter(request, "story:"))
                            .replaceFirst("(draft " + (draftNumber(request) + 1) + ")"));
        }

        private static int draftNumber(ChatRequest request) {
            Matcher matcher = DRAFT_NUMBER.matcher(lastUserMessage(request));
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
        }
    }
}
