package io.quarkiverse.langchain4j.agentic.viewer.test.patterns;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.agentic.patterns.p2p.P2PPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;

/**
 * Fixture app with one planner of each langchain4j-agentic-patterns pattern.
 */
public interface PatternSystems {

    interface Writer {
        @Agent(description = "Writes", outputKey = "draft")
        String write(@V("topic") String topic);
    }

    interface Goal {
        @PlannerAgent(outputKey = "draft", subAgents = { Writer.class })
        String plan(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner() {
            return new GoalOrientedPlanner();
        }
    }

    interface Peers {
        @PlannerAgent(outputKey = "draft", subAgents = { Writer.class })
        String plan(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner() {
            return new P2PPlanner(3, scope -> scope.hasState("draft"));
        }
    }

    interface Board {
        @PlannerAgent(outputKey = "draft", subAgents = { Writer.class })
        String plan(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner() {
            return new BlackboardPlanner();
        }
    }

    @ApplicationScoped
    class EntryPoint {
        @Inject
        Goal goal;
        @Inject
        Peers peers;
        @Inject
        Board board;
    }

    @ApplicationScoped
    class FixtureChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("fixture")).build();
        }
    }
}
