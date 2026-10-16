package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Test-only system whose human-in-the-loop step answers an int: the Dev UI's answer is converted from JSON.
 */
public interface CopiesOrder {

    @SequenceAgent(outputKey = "order", subAgents = { ProductFinder.class, CopiesQuestion.class, OrderWriter.class })
    String order(@V("request") String request);

    interface ProductFinder {
        @UserMessage("Find the product in: {{request}}")
        @Agent(description = "Finds the product", outputKey = "product")
        String find(@V("request") String request);
    }

    // a step's arguments must be written by an agent: the build rejects one only given to the root
    interface CopiesQuestion {
        @HumanInTheLoop(description = "How many copies?", outputKey = "copies")
        static int askCopies(@V("product") String product) {
            return 1;
        }
    }

    interface OrderWriter {
        @UserMessage("Write the order of {{copies}} copies of {{product}}")
        @Agent(description = "Writes the order", outputKey = "order")
        String write(@V("product") String product, @V("copies") int copies);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Find the product in:"))
                    .reply(request -> userMessageAfter(request, "in:"))
                    .when(userMessageContains("Write the order of"))
                    .reply(request -> "Order: " + userMessageAfter(request, "order of"));
        }
    }
}
