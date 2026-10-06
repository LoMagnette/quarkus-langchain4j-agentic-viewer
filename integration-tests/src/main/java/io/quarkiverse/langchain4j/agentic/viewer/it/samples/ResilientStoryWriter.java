package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Error handler: when the topic is missing, the writer fails and the handler retries with a default topic.
 */
public interface ResilientStoryWriter {

    String DEFAULT_TOPIC = "dragons and wizards";

    @SequenceAgent(description = "Writes a story, falling back to a default topic", outputKey = "story", subAgents = {
            TopicWriter.class })
    String write(@V("topic") String topic);

    @ErrorHandler
    static ErrorRecoveryResult recover(ErrorContext errorContext) {
        if (errorContext.exception() instanceof MissingArgumentException missing
                && missing.argumentName().equals("topic")) {
            errorContext.agenticScope().writeState("topic", DEFAULT_TOPIC);
            return ErrorRecoveryResult.retry();
        }
        return ErrorRecoveryResult.throwException();
    }

    interface TopicWriter {
        @UserMessage("Write a very short story about {{topic}}")
        @Agent(description = "Writes a very short story about a topic", outputKey = "story")
        String writeShortStory(@V("topic") String topic);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Write a very short story about"))
                    .reply(request -> "A tiny tale of " + userMessageAfter(request, "story about") + ".");
        }
    }
}
