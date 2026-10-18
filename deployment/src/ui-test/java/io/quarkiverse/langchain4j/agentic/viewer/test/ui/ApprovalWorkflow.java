package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;

/**
 * Fixture app of the UI tests: a draft, then a human-in-the-loop approval answered in the Dev UI.
 */
public interface ApprovalWorkflow {

    @SequenceAgent(outputKey = "approval", subAgents = { Drafter.class, Approval.class })
    String approve(@V("subject") String subject);

    interface Drafter {
        @dev.langchain4j.service.UserMessage("Draft a note about {{subject}}")
        @Agent(description = "Drafts a note", outputKey = "draft")
        String draft(@V("subject") String subject);
    }

    // a step's arguments must be written by an agent: the build rejects one only given to the root
    interface Approval {
        @HumanInTheLoop(description = "Approve the draft?", outputKey = "approval")
        static String approve(@V("draft") String draft) {
            return "approved by the application";
        }
    }

    /** Injecting the root agent marks its parameters as provided by the caller. */
    @ApplicationScoped
    class EntryPoint {
        @Inject
        ApprovalWorkflow workflow;
    }
}
