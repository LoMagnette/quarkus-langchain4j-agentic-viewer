package io.quarkiverse.langchain4j.agentic.viewer.test.contrib;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.workflow.impl.SequentialPlanner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.AgenticTopologyContributor;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.ContributedEdge;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.ContributedNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.TopologyContribution;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;

/**
 * Fixture app: a planner agent with two sub-agents, described by topology contributors.
 */
public interface ReviewSystem {

    @PlannerAgent(outputKey = "text", subAgents = { Writer.class, Reviewer.class })
    String review(String topic);

    @PlannerSupplier
    static Planner planner() {
        return new SequentialPlanner();
    }

    interface Writer {
        @Agent(description = "Writes", outputKey = "text")
        String write(String topic);
    }

    interface Reviewer {
        @Agent(description = "Reviews", outputKey = "verdict")
        String check(String text);
    }

    /** Draws the review loop: references by agent name, by node id and to a contributed node; one bad edge. */
    class ReviewTopology implements AgenticTopologyContributor {
        @Override
        public Class<?> plannerAgent() {
            return ReviewSystem.class;
        }

        @Override
        public TopologyContribution contribute(AgentNode plannerNode) {
            return new TopologyContribution(List.of(new ContributedNode("done", "approved")),
                    List.of(new ContributedEdge("write", "check", "text"),
                            new ContributedEdge(plannerNode.children().get(1).id(), "write", "rejected"),
                            new ContributedEdge("check", "done", null),
                            new ContributedEdge("check", "nowhere", "ignored")));
        }
    }

    /** Fails: logged and ignored. */
    class FailingTopology implements AgenticTopologyContributor {
        @Override
        public Class<?> plannerAgent() {
            return ReviewSystem.class;
        }

        @Override
        public TopologyContribution contribute(AgentNode plannerNode) {
            throw new IllegalStateException("broken contributor");
        }
    }

    /** Describes another planner agent: not applied here. */
    class OtherTopology implements AgenticTopologyContributor {
        @Override
        public Class<?> plannerAgent() {
            return Writer.class;
        }

        @Override
        public TopologyContribution contribute(AgentNode plannerNode) {
            return new TopologyContribution(List.of(new ContributedNode("other", "other")), List.of());
        }
    }

    @ApplicationScoped
    class EntryPoint {
        @Inject
        ReviewSystem system;
    }

    @ApplicationScoped
    class FixtureChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("fixture")).build();
        }
    }
}
