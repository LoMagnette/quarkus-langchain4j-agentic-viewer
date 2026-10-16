package io.quarkiverse.langchain4j.agentic.viewer.test.reload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;

/**
 * Fixture app: a run of it waits on its human-in-the-loop step until the Dev UI answers, so it stays open.
 */
public interface WaitingWorkflow {

    @SequenceAgent(outputKey = "approval", subAgents = { Ask.class })
    String run(@V("topic") String topic);

    interface Ask {
        @HumanInTheLoop(description = "Approve the topic?", outputKey = "approval")
        static String ask() {
            return "yes";
        }
    }

    @ApplicationScoped
    class EntryPoint {
        @Inject
        WaitingWorkflow workflow;
    }

    /** The build wants a chat model even though no agent here calls one. */
    @ApplicationScoped
    class FixtureChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("fixture")).build();
        }
    }
}
