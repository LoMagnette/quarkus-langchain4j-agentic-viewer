package io.quarkiverse.langchain4j.agentic.viewer.it.fake;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.lastUserMessage;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.systemMessageContains;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * Rule matching and reply shapes. End-to-end behaviour through langchain4j-agentic is covered by
 * {@link ScriptedChatModelAgenticTest}.
 */
class ScriptedChatModelTest {

    @Test
    void firstMatchingRuleWins() {
        ScriptedChatModel model = ScriptedChatModel.builder()
                .when(systemMessageContains("pirate")).reply("Arr")
                .when(userMessageContains("hello")).reply("Hi")
                .when(userMessageContains("hello")).reply("never used")
                .build();

        assertEquals("Hi", model.chat(request(UserMessage.from("hello there"))).aiMessage().text());
        assertEquals("Arr", model.chat(request(SystemMessage.from("You are a pirate"), UserMessage.from("hello")))
                .aiMessage().text());
    }

    @Test
    void replyCanBeComputedFromTheRequest() {
        ScriptedChatModel model = ScriptedChatModel.builder()
                .when(userMessageContains("Echo")).reply(request -> lastUserMessage(request).toUpperCase())
                .build();

        assertEquals("ECHO THIS", model.chat(request(UserMessage.from("Echo this"))).aiMessage().text());
    }

    @Test
    void unmatchedRequestFailsWithItsContent() {
        ScriptedChatModel model = ScriptedChatModel.builder()
                .when(userMessageContains("known")).reply("ok")
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> model.chat(request(UserMessage.from("something unexpected"))));
        assertTrue(error.getMessage().contains("something unexpected"), error.getMessage());
    }

    @Test
    void recordsServedRequests() {
        ScriptedChatModel model = ScriptedChatModel.builder()
                .when(userMessageContains("")).reply("ok")
                .build();

        model.chat(request(UserMessage.from("first")));
        model.chat(request(UserMessage.from("second")));

        assertEquals(List.of("first", "second"), model.requests().stream().map(ScriptedChatModel::lastUserMessage).toList());
        model.clearRequests();
        assertTrue(model.requests().isEmpty());
    }

    @Test
    void supervisorReplyEscapesTheResponse() {
        assertEquals("\"say \\\"hi\\\"\\nnow\"", ScriptedChatModel.jsonString("say \"hi\"\nnow"));
    }

    private static ChatRequest request(dev.langchain4j.data.message.ChatMessage... messages) {
        return ChatRequest.builder().messages(messages).build();
    }
}
