package io.quarkiverse.langchain4j.agentic.viewer.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Fixture app: a declarative sequence of two AI agents.
 */
public interface StoryWorkflow {

    @SequenceAgent(outputKey = "story", subAgents = { Writer.class, Editor.class })
    String writeStory(String topic);

    interface Writer {
        @Agent(description = "Writes a draft", outputKey = "draft")
        String write(String topic);
    }

    interface Editor {
        @Agent(description = "Edits the draft", outputKey = "story")
        String edit(String draft);
    }

    /**
     * Injecting the root agent marks its parameters as provided by the caller.
     */
    @ApplicationScoped
    class EntryPoint {
        @Inject
        StoryWorkflow workflow;
    }

    @ApplicationScoped
    class FixtureChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("fixture")).build();
        }
    }
}
