package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Type;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentSettings;
import io.quarkiverse.langchain4j.agentic.viewer.test.scan.AiAgentSuppliersScanTest;

/**
 * An AI agent of a builder system takes from the annotations of its type what the scanner reads at build time: model
 * suppliers, skills, the other suppliers in its code, its settings.
 */
class BuilderAgentDetailsTest {

    /** An agent built from an annotated type, as the public AgentInstance API shows it. */
    record Agent(Class<?> type) implements AgentInstance {
        @Override
        public Class<? extends Planner> plannerType() {
            return null;
        }

        @Override
        public String name() {
            return "answer";
        }

        @Override
        public String agentId() {
            return "answer";
        }

        @Override
        public String description() {
            return null;
        }

        @Override
        public Type outputType() {
            return String.class;
        }

        @Override
        public String outputKey() {
            return "answer";
        }

        @Override
        public boolean async() {
            return false;
        }

        @Override
        public List<AgentArgument> arguments() {
            return List.of();
        }

        @Override
        public AgentInstance parent() {
            return null;
        }

        @Override
        public List<AgentInstance> subagents() {
            return List.of();
        }

        @Override
        public AgenticSystemTopology topology() {
            return AgenticSystemTopology.AI_AGENT;
        }
    }

    private static AgentNode node(Class<?> type) {
        return BuilderTopology.system("test#system", "Test", null, new Agent(type)).root();
    }

    @Test
    void suppliersAndSkillsFromTheAgentType() {
        AgentNode assistant = node(AiAgentSuppliersScanTest.Assistant.class);
        String type = AiAgentSuppliersScanTest.Assistant.class.getName();
        assertEquals(type + "#model", assistant.aiDetails().chatModelSupplier());
        assertEquals(List.of("billing", "refunds"), assistant.aiDetails().skills());
        assertEquals(List.of("AGENT answer", "CHAT_MODEL_SUPPLIER model", "CHAT_MEMORY_SUPPLIER memory",
                "CHAT_MEMORY_PROVIDER_SUPPLIER memories", "CONTENT_RETRIEVER_SUPPLIER retriever",
                "RETRIEVAL_AUGMENTOR_SUPPLIER augmentor", "SYSTEM_MESSAGE_PROVIDER systemMessage",
                "USER_MESSAGE_PROVIDER userMessage", "AGENT_LISTENER_SUPPLIER listener"),
                assistant.code().stream().map(ref -> ref.role() + " " + ref.member()).toList());
        assertEquals(new AgentSettings(false, List.of(), null, null, null), assistant.settings());

        AgentNode streamer = node(AiAgentSuppliersScanTest.Streamer.class);
        assertEquals(AiAgentSuppliersScanTest.Streamer.class.getName() + "#model",
                streamer.aiDetails().streamingChatModelSupplier());
        assertEquals(List.of(), streamer.aiDetails().skills());
    }
}
