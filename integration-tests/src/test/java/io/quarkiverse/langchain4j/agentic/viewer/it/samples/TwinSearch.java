package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Test-only parallel system whose branches can be held until a number of them run, so that runs overlap.
 */
public interface TwinSearch {

    @ParallelAgent(outputKey = "found", subAgents = { LeftSearch.class, RightSearch.class })
    String search(@V("topic") String topic);

    @Output
    static String combine(@V("left") String left, @V("right") String right) {
        return left + " + " + right;
    }

    interface LeftSearch {
        @UserMessage("Twin search left: {{topic}}")
        @Agent(description = "Searches on the left", outputKey = "left")
        String search(@V("topic") String topic);
    }

    interface RightSearch {
        @UserMessage("Twin search right: {{topic}}")
        @Agent(description = "Searches on the right", outputKey = "right")
        String search(@V("topic") String topic);
    }

    /** Branches that started; each waits until the latch reaches zero (5 s at most). */
    final class Gate {
        private static volatile CountDownLatch latch = new CountDownLatch(0);

        private Gate() {
        }

        public static void holdUntil(int branches) {
            latch = new CountDownLatch(branches);
        }

        static void pass() {
            CountDownLatch current = latch;
            current.countDown();
            try {
                current.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Twin search left:"))
                    .reply(request -> {
                        Gate.pass();
                        return "left " + userMessageAfter(request, "left:");
                    })
                    .when(userMessageContains("Twin search right:"))
                    .reply(request -> {
                        Gate.pass();
                        return "right " + userMessageAfter(request, "right:");
                    });
        }
    }
}
