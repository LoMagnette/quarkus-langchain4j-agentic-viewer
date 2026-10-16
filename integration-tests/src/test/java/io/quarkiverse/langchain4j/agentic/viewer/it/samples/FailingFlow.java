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
 * Test-only system whose agent always fails (its model call throws), with no @ErrorHandler: the error is fatal.
 */
public interface FailingFlow {

    @SequenceAgent(outputKey = "result", subAgents = { Exploder.class })
    String run(@V("text") String text);

    interface Exploder {
        @UserMessage("Explode on: {{text}}")
        @Agent(description = "Always fails", outputKey = "result")
        String explode(@V("text") String text);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Explode on:")).reply(request -> {
                throw new IllegalStateException("boom on " + userMessageAfter(request, "on:"));
            });
        }
    }
}
