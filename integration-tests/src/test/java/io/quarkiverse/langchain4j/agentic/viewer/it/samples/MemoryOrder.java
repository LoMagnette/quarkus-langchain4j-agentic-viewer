package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Test-only system whose root takes a memory id: its scope outlives the root call (langchain4j only evicts ephemeral
 * scopes), so a later call with the same memory id reuses it.
 */
public interface MemoryOrder {

    @SequenceAgent(outputKey = "order", subAgents = { ItemFinder.class, ItemApproval.class, OrderWriter.class })
    String order(@MemoryId String customer, @V("request") String request);

    interface ItemFinder {
        @UserMessage("Find the item in: {{request}}")
        @Agent(description = "Finds the item", outputKey = "item")
        String find(@V("request") String request);
    }

    // a step's arguments must be written by an agent: the build rejects one only given to the root
    interface ItemApproval {
        @HumanInTheLoop(description = "Approve the item?", outputKey = "approval")
        static String approve(@V("item") String item) {
            return HumanInput.ask("Approve " + item);
        }
    }

    interface OrderWriter {
        @UserMessage("Write the memory order of {{item}}: {{approval}}")
        @Agent(description = "Writes the order", outputKey = "order")
        String write(@V("item") String item, @V("approval") String approval);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Find the item in:"))
                    .reply(request -> userMessageAfter(request, "in:"))
                    .when(userMessageContains("Write the memory order of"))
                    .reply(request -> "Order of " + userMessageAfter(request, "order of"));
        }
    }
}
