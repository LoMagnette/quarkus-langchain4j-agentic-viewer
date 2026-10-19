package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.BeforeCall;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatMemorySupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ContentRetrieverSupplier;
import dev.langchain4j.agentic.declarative.RetrievalAugmentorSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.StreamingChatModelSupplier;
import dev.langchain4j.agentic.declarative.SystemMessageProviderSupplier;
import dev.langchain4j.agentic.declarative.UserMessageProviderSupplier;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.CodeRef;
import io.quarkiverse.langchain4j.skills.Skills;

/**
 * The model suppliers and skills of an AI agent are in its details; every supplier and {@code @BeforeCall} next to an
 * agent is in its code.
 */
public class AiAgentSuppliersScanTest {

    public interface Assistant {
        @Agent(outputKey = "answer")
        @Skills({ "billing", "refunds" })
        String answer(@V("question") String question);

        @ChatModelSupplier
        static ChatModel model() {
            return null;
        }

        @ChatMemorySupplier
        static ChatMemory memory() {
            return null;
        }

        @ChatMemoryProviderSupplier
        static ChatMemoryProvider memories() {
            return null;
        }

        @ContentRetrieverSupplier
        static ContentRetriever retriever() {
            return null;
        }

        @RetrievalAugmentorSupplier
        static RetrievalAugmentor augmentor() {
            return null;
        }

        @SystemMessageProviderSupplier
        static String systemMessage(Object memoryId) {
            return null;
        }

        @UserMessageProviderSupplier
        static String userMessage(Object memoryId) {
            return null;
        }

        @AgentListenerSupplier
        static AgentListener listener() {
            return null;
        }
    }

    public interface Streamer {
        @Agent(outputKey = "answer")
        @Skills
        String answer(@V("question") String question);

        @StreamingChatModelSupplier
        static StreamingChatModel model() {
            return null;
        }
    }

    interface Plain {
        @Agent(outputKey = "answer")
        String answer(@V("question") String question);
    }

    interface Flow {
        @SequenceAgent(outputKey = "answer", subAgents = { Plain.class })
        String run(@V("question") String question);

        @BeforeCall
        static void prepare(AgenticScope scope) {
        }
    }

    private static Map<String, AgentDescriptor> byClass;

    @BeforeAll
    static void scan() throws Exception {
        byClass = new AgenticAnnotationScanner().scan(Index.of(Assistant.class, Streamer.class, Plain.class, Flow.class))
                .stream().collect(Collectors.toMap(AgentDescriptor::declaringClass, Function.identity()));
    }

    private static AgentDescriptor get(Class<?> type) {
        return byClass.get(type.getName());
    }

    @Test
    void modelSuppliersAndSkills() {
        assertEquals(Assistant.class.getName() + "#model", get(Assistant.class).aiDetails().chatModelSupplier());
        assertNull(get(Assistant.class).aiDetails().streamingChatModelSupplier());
        assertEquals(List.of("billing", "refunds"), get(Assistant.class).aiDetails().skills());
        assertEquals(Streamer.class.getName() + "#model", get(Streamer.class).aiDetails().streamingChatModelSupplier());
        assertEquals(List.of(), get(Streamer.class).aiDetails().skills(), "@Skills without values");
        assertNull(get(Plain.class).aiDetails().skills(), "no @Skills");
        assertNull(get(Plain.class).aiDetails().chatModelSupplier());
    }

    @Test
    void suppliersAreInTheCode() {
        assertEquals(List.of("AGENT answer", "CHAT_MODEL_SUPPLIER model", "CHAT_MEMORY_SUPPLIER memory",
                "CHAT_MEMORY_PROVIDER_SUPPLIER memories", "CONTENT_RETRIEVER_SUPPLIER retriever",
                "RETRIEVAL_AUGMENTOR_SUPPLIER augmentor", "SYSTEM_MESSAGE_PROVIDER systemMessage",
                "USER_MESSAGE_PROVIDER userMessage", "AGENT_LISTENER_SUPPLIER listener"), code(Assistant.class));
        assertEquals(List.of("AGENT run", "BEFORE_CALL prepare"), code(Flow.class));
    }

    private static List<String> code(Class<?> type) {
        return get(type).code().stream().map(ref -> ref.role() + " " + ref.member()).toList();
    }

    @Test
    void theDevUiLabelsEveryRole() throws Exception {
        String script;
        try (var in = getClass().getClassLoader().getResourceAsStream("dev-ui/qwc-agentic-node-details.js")) {
            script = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String roles = script.substring(script.indexOf("const CODE_ROLES"), script.indexOf("};", script.indexOf("const CODE_ROLES")));
        for (CodeRef.Role role : CodeRef.Role.values()) {
            assertTrue(roles.contains(role.name() + ":"), role + " has no label in CODE_ROLES");
        }
    }
}
