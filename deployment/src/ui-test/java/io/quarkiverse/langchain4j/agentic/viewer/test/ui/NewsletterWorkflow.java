package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;

/**
 * Fixture app of the UI tests: a sequence of two AI agents, with a text input and a JSON (list) input.
 */
public interface NewsletterWorkflow {

    @SequenceAgent(outputKey = "newsletter", subAgents = { Researcher.class, Writer.class })
    String write(@V("topic") String topic, @V("sources") List<String> sources);

    interface Researcher {
        @dev.langchain4j.service.UserMessage("Collect facts about {{topic}} from {{sources}}")
        @Agent(description = "Collects facts", outputKey = "facts")
        String research(@V("topic") String topic, @V("sources") List<String> sources);
    }

    interface Writer {
        @dev.langchain4j.service.UserMessage("Write a newsletter from: {{facts}}")
        @Agent(description = "Writes the newsletter", outputKey = "newsletter")
        String write(@V("facts") String facts);
    }

    /** Injecting the root agent marks its parameters as provided by the caller. */
    @ApplicationScoped
    class EntryPoint {
        @Inject
        NewsletterWorkflow workflow;
    }

    /**
     * Echoes the prompt after a pause, long enough for the browser to see each agent running; the output then tells
     * which inputs a run had.
     */
    @ApplicationScoped
    class SlowEchoChatModel implements ChatModel {

        static final long PAUSE_MILLIS = 1200;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            try {
                Thread.sleep(PAUSE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<ChatMessage> messages = chatRequest.messages();
            String prompt = messages.get(messages.size() - 1) instanceof UserMessage user ? user.singleText() : "";
            return ChatResponse.builder().aiMessage(AiMessage.from("echo: " + prompt)).build();
        }
    }
}
