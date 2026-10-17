package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryCreator;
import io.quarkus.arc.Unremovable;

/**
 * Declarative workflow using a builder sub-agent: its first step is a non-AI agent delegating to the
 * {@link BuilderStoryCreator} system, then an AI agent writes a blurb. The builder system runs as a separate root
 * invocation (its own scope): the declarative graph only shows the delegating step.
 */
public interface BlurbedStory {

    @SequenceAgent(description = "Creates a story with the builder system, then writes its blurb", outputKey = "blurb", subAgents = {
            StoryStep.class, BlurbWriter.class })
    String create(@V("topic") String topic);

    /** A bean: quarkus-langchain4j-agentic takes non-AI agent classes from CDI (looked up, so kept unremovable). */
    @ApplicationScoped
    @Unremovable
    class StoryStep {

        @Inject
        @Named(BuilderStoryCreator.NAME)
        UntypedAgent storyCreator;

        @Agent(description = "Creates the story with the builder StoryCreator", outputKey = "story")
        public String story(@V("topic") String topic) {
            return String.valueOf(storyCreator.invoke(Map.of("topic", topic)));
        }
    }

    interface BlurbWriter {
        @UserMessage("Write a one-line blurb for this story: {{story}}")
        @Agent(description = "Writes the blurb of the story", outputKey = "blurb")
        String blurb(@V("story") String story);
    }

    /** {@link StoryCreator.Script} answers the builder system. */
    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Write a one-line blurb for this story:"))
                    .reply("A tale you will not forget.");
        }
    }
}
