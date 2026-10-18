package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;

/**
 * Fixture app of the UI tests: a system with optional inputs (a list and a boolean), never run.
 */
public interface OptionsWorkflow {

    @SequenceAgent(outputKey = "note", subAgents = { NoteTaker.class })
    String take(@V("subject") String subject, @V("tags") Optional<List<String>> tags,
            @V("urgent") Optional<Boolean> urgent);

    interface NoteTaker {
        @dev.langchain4j.service.UserMessage("Take a note about {{subject}}")
        @Agent(description = "Takes a note", outputKey = "note")
        String take(@V("subject") String subject);
    }

    /** Injecting the root agent marks its parameters as provided by the caller. */
    @ApplicationScoped
    class EntryPoint {
        @Inject
        OptionsWorkflow workflow;
    }
}
