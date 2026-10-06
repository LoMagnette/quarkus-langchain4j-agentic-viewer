package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;

/**
 * One agentic system per workflow annotation, valid for quarkus-langchain4j-agentic so it also builds as an app.
 */
public interface ScanFixtures {

    // ---- sequence ----

    interface StoryFlow {
        @SequenceAgent(name = "storyFlow", description = "Writes then reviews", outputKey = "review", subAgents = {
                Writer.class, Reviewer.class })
        String write(@V("topic") String topic);
    }

    interface Writer {
        @Agent(value = "Writes a draft", outputKey = "draft")
        String write(@V("topic") String topic);
    }

    interface Reviewer {
        @Agent(name = "reviewer", description = "Reviews the draft", outputKey = "review")
        String review(@V("draft") String draft, AgenticScope scope);
    }

    // ---- parallel ----

    interface ResearchFan {
        @ParallelAgent(outputKey = "research", subAgents = { WebSearch.class, DbSearch.class })
        String research(@V("topic") String topic);
    }

    interface WebSearch {
        @Agent(outputKey = "web")
        String searchWeb(@V("topic") String topic);
    }

    interface DbSearch {
        @Agent(outputKey = "db")
        String searchDb(@V("topic") String topic);
    }

    // ---- parallel mapper ----

    interface Batch {
        @ParallelMapperAgent(outputKey = "translations", subAgent = Translator.class, itemsProvider = "items")
        List<String> translateAll(@V("items") List<String> items);
    }

    interface Translator {
        @Agent(outputKey = "translation")
        String translate(@V("item") String item);
    }

    // ---- loop, with a typed output key ----

    interface Polisher {
        @LoopAgent(outputKey = "draft", maxIterations = 3, subAgents = { Scorer.class, Editor.class })
        String polish(@V("draft") String draft);
    }

    class Score implements TypedKey<Double> {
    }

    interface Scorer {
        @Agent(typedOutputKey = Score.class)
        double score(@V("draft") String draft);
    }

    interface Editor {
        @Agent(outputKey = "draft")
        String edit(@V("draft") String draft);
    }

    // ---- conditional ----

    interface Router {
        @ConditionalAgent(outputKey = "answer", subAgents = { Expert.class })
        String route(@V("topic") String topic);

        @ActivationCondition(Expert.class)
        static boolean always(@V("topic") String topic) {
            return true;
        }
    }

    interface Expert {
        @Agent(outputKey = "answer")
        String answer(@V("topic") String topic);
    }

    // ---- non-AI agent ----

    class Shouter {
        @Agent(description = "Upper-cases the draft", outputKey = "shout")
        public String shout(@V("draft") String draft) {
            return draft.toUpperCase();
        }
    }

    // ---- app plumbing ----

    /**
     * Injecting the roots marks their parameters as provided by the caller (build-time validation).
     */
    @ApplicationScoped
    class EntryPoints {
        @Inject
        StoryFlow storyFlow;
        @Inject
        ResearchFan researchFan;
        @Inject
        Batch batch;
        @Inject
        Polisher polisher;
        @Inject
        Router router;
    }

    @ApplicationScoped
    class FixtureChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("fixture")).build();
        }
    }
}
