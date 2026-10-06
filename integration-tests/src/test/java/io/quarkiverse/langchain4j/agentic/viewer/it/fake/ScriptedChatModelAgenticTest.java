package io.quarkiverse.langchain4j.agentic.viewer.it.fake;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastMessageIsToolResult;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastToolResult;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.supervisorLastResponseContains;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Predicate;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The scripted replies drive real declarative agents built by quarkus-langchain4j-agentic:
 * tool calls are executed and supervisor decisions are parsed. The agents use the application's
 * default chat model, i.e. the scripted model with the rules of {@link TestAgentsScript}.
 */
@QuarkusTest
class ScriptedChatModelAgenticTest {

    static final String SUPERVISOR_REQUEST = "Write about cats, then translate it to French";

    /**
     * Scripts for the test agents, contributed to the application's scripted model.
     */
    @ApplicationScoped
    public static class TestAgentsScript implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script
                    // tool flow
                    .when(userMessageContains("Convert 100 EUR").and(lastMessageIsToolResult("exchange")))
                    .reply(request -> "You get " + lastToolResult(request))
                    .when(userMessageContains("Convert 100 EUR")).callTool("exchange", "{\"amount\": 100}")
                    // supervisor planning steps for this test's request, chosen from the last agent response
                    .when(planning("")).invokeAgent("write", "{\"topic\": \"cats\"}")
                    .when(planning("Cats are great")).invokeAgent("translate", "{\"text\": \"Cats are great\"}")
                    .when(planning("Les chats")).finishSupervisor("Wrote and translated")
                    // supervisor sub-agents
                    .when(userMessageContains("Write about cats")).reply("Cats are great")
                    .when(userMessageContains("Translate to French: Cats are great")).reply("Les chats sont super");
        }

        private static Predicate<ChatRequest> planning(String lastResponse) {
            return supervisorLastResponseContains(lastResponse).and(userMessageContains(SUPERVISOR_REQUEST));
        }
    }

    public static class ExchangeTools {
        @Tool("Exchanges euros to dollars")
        public String exchange(int amount) {
            return amount * 2 + " USD";
        }
    }

    public interface Converter {
        @Agent(description = "Converts euros to dollars", outputKey = "dollars")
        @UserMessage("Convert {{amount}} EUR to USD")
        String convert(@V("amount") int amount);

        @ToolsSupplier
        static Object tools() {
            return new ExchangeTools();
        }
    }

    public interface Writer {
        @Agent(description = "Writes a short text about a topic", outputKey = "text")
        @UserMessage("Write about {{topic}}")
        String write(@V("topic") String topic);
    }

    public interface Translator {
        @Agent(description = "Translates a text to French", outputKey = "translation")
        @UserMessage("Translate to French: {{text}}")
        String translate(@V("text") String text);
    }

    public interface WriteAndTranslate {
        @SupervisorAgent(outputKey = "result", responseStrategy = SupervisorResponseStrategy.SUMMARY, subAgents = {
                Writer.class, Translator.class })
        String run(@V("request") String request);
    }

    @Inject
    Converter converter;

    @Inject
    WriteAndTranslate supervisor;

    @Inject
    ScriptedChatModel model;

    @BeforeEach
    void clearRequests() {
        model.clearRequests();
    }

    @Test
    void scriptsToolCallsThroughAnAgent() {
        assertEquals("You get 200 USD", converter.convert(100));
        assertEquals(2, model.requests().size(), "Tool request, then answer from the tool result");
    }

    @Test
    void scriptsSupervisorDecisions() {
        assertEquals("Wrote and translated", supervisor.run(SUPERVISOR_REQUEST));

        List<String> agentRequests = model.requests().stream()
                .map(ScriptedChatModel::lastUserMessage)
                .filter(message -> !message.contains("The last received response is"))
                .toList();
        assertEquals(List.of("Write about cats", "Translate to French: Cats are great"), agentRequests);
    }
}
