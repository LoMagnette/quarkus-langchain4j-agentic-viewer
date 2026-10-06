package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Sequence: an introduction, then an adventure, combined by an {@link Output} method.
 */
public interface StoryWriter {

    @SequenceAgent(description = "Writes a short story about a person", outputKey = "story", subAgents = {
            IntroWriter.class, AdventureWriter.class })
    String writeStory(@V("bio") String bio);

    @Output
    static String story(@V("intro") String intro, @V("adventure") String adventure) {
        return intro + "\n\n" + adventure;
    }

    interface IntroWriter {
        @SystemMessage("You are a storyteller writing for children.")
        @UserMessage("Write the introduction of a story about this person: {{bio}}")
        @Agent(description = "Writes the introduction of the story", outputKey = "intro")
        String writeIntro(@V("bio") String bio);
    }

    interface AdventureWriter {
        @SystemMessage("You are a storyteller writing for children.")
        @UserMessage("Continue this story with an adventure: {{intro}}")
        @Agent(description = "Writes the adventure part of the story", outputKey = "adventure")
        String writeAdventure(@V("intro") String intro);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Write the introduction of a story about this person:"))
                    .reply(request -> "Once upon a time, there was " + userMessageAfter(request, "person:") + ".")
                    .when(userMessageContains("Continue this story with an adventure:"))
                    .reply("One day, a dragon knocked at the door and asked for help.");
        }
    }
}
