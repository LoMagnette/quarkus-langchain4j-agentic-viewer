package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Fixture app of the UI tests: wiring worth a warning (a loop without exit condition always runs its 10 iterations)
 * and a hint (the agent reads a note only another system writes: quarkus-langchain4j-agentic checks keys
 * application-wide, the Dev UI per system); never run.
 */
public interface LoopingWorkflow {

    @LoopAgent(outputKey = "draft", subAgents = { Reworder.class })
    String rework(@V("draft") String draft);

    interface Reworder {
        @UserMessage("Reword {{draft}} after {{note}}")
        @Agent(description = "Rewords the draft", outputKey = "draft")
        String reword(@V("draft") String draft, @V("note") String note);
    }

    /** Injecting the root agent marks its parameters as provided by the caller. */
    @ApplicationScoped
    class EntryPoint {
        @Inject
        LoopingWorkflow workflow;
    }
}
