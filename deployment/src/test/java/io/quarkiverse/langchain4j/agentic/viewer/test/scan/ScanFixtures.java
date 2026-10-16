package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.impl.SequentialPlanner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;

/**
 * One agentic system per workflow, supervisor, planner and human-in-the-loop annotation, valid for quarkus-langchain4j-agentic so it also builds as an app.
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

    // ---- loop, with an exit condition, an @Output method and a typed output key ----

    interface Polisher {
        @LoopAgent(outputKey = "draft", maxIterations = 3, subAgents = { Scorer.class, Editor.class })
        String polish(@V("draft") String draft);

        @ExitCondition(testExitAtLoopEnd = true)
        static boolean goodEnough(@V("draft") String draft) {
            return draft.length() > 10;
        }

        @Output
        static String finalDraft(@V("draft") String draft) {
            return draft.trim();
        }
    }

    /** Defaults: maxIterations 10, no exit condition. */
    interface Repeater {
        @LoopAgent(outputKey = "draft", subAgents = { Editor.class })
        String repeat(@V("draft") String draft);
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
        @ConditionalAgent(outputKey = "answer", subAgents = { Expert.class, Generalist.class })
        String route(@V("topic") String topic);

        @ActivationCondition(Expert.class)
        static boolean technical(@V("topic") String topic) {
            return topic.contains("java");
        }

        @ActivationCondition(Generalist.class)
        static boolean general(@V("topic") String topic) {
            return !topic.contains("java");
        }
    }

    interface Expert {
        @Agent(outputKey = "answer")
        String answer(@V("topic") String topic);
    }

    interface Generalist {
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

    // ---- supervisor ----

    interface Bank {
        @SupervisorAgent(description = "Banking supervisor", outputKey = "result", subAgents = { Teller.class })
        String transact(@V("request") String request);
    }

    interface Teller {
        @Agent(description = "Moves money", outputKey = "receipt")
        String move(@V("amount") double amount);
    }

    // ---- planner ----

    interface DraftPlanner {
        @PlannerAgent(outputKey = "draft", subAgents = { Writer.class })
        String plan(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner() {
            return new SequentialPlanner();
        }
    }

    // ---- human in the loop ----

    interface Approval {
        @HumanInTheLoop(value = "Asks for approval", outputKey = "approval")
        static String ask(@V("draft") String draft) {
            return "yes";
        }
    }

    interface ApprovedStory {
        @SequenceAgent(outputKey = "approval", subAgents = { Writer.class, Approval.class })
        String write(@V("topic") String topic);
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
        Repeater repeater;
        @Inject
        Router router;
        @Inject
        Bank bank;
        @Inject
        DraftPlanner draftPlanner;
        @Inject
        ApprovedStory approvedStory;
    }

    @ApplicationScoped
    class FixtureChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("fixture")).build();
        }
    }
}
