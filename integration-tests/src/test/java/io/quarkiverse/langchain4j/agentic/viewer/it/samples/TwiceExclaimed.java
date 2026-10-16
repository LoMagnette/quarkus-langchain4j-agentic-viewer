package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Test-only system using the same sub-agent twice: its two invocations must map to two distinct nodes.
 */
public interface TwiceExclaimed {

    @SequenceAgent(outputKey = "text", subAgents = { Exclaimer.class, Exclaimer.class })
    String exclaim(@V("text") String text);

    interface Exclaimer {
        @UserMessage("Add an exclamation mark to: {{text}}")
        @Agent(description = "Adds an exclamation mark", outputKey = "text")
        String exclaim(@V("text") String text);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Add an exclamation mark to:"))
                    .reply(request -> userMessageAfter(request, "to:") + "!");
        }
    }
}
