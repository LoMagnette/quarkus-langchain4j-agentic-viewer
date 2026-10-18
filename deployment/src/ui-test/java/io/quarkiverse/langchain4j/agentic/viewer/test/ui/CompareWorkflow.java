package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Fixture app of the UI tests: runs of it differ by their inputs: the loop iterates until the text reaches the limit
 * (each echo adds 13 characters), the conditional takes the fast branch when urgent.
 */
public interface CompareWorkflow {

    @SequenceAgent(outputKey = "result", subAgents = { Polish.class, Route.class })
    String run(@V("text") String text, @V("limit") int limit, @V("urgent") boolean urgent);

    interface Polish {
        @LoopAgent(outputKey = "text", maxIterations = 5, subAgents = { Grow.class })
        String polish(@V("text") String text, @V("limit") int limit);

        @ExitCondition
        static boolean longEnough(@V("text") String text, @V("limit") int limit) {
            return text.length() >= limit;
        }
    }

    interface Grow {
        @UserMessage("Grow: {{text}}")
        @Agent(description = "Grows the text", outputKey = "text")
        String grow(@V("text") String text);
    }

    interface Route {
        @ConditionalAgent(outputKey = "result", subAgents = { Fast.class, Slow.class })
        String route(@V("text") String text, @V("urgent") boolean urgent);

        @ActivationCondition(Fast.class)
        static boolean urgent(@V("urgent") boolean urgent) {
            return urgent;
        }

        @ActivationCondition(Slow.class)
        static boolean notUrgent(@V("urgent") boolean urgent) {
            return !urgent;
        }
    }

    interface Fast {
        @UserMessage("Fast: {{text}}")
        @Agent(description = "Answers at once", outputKey = "result")
        String fast(@V("text") String text);
    }

    interface Slow {
        @UserMessage("Slow: {{text}}")
        @Agent(description = "Answers carefully", outputKey = "result")
        String slow(@V("text") String text);
    }

    /** Injecting the root agent marks its parameters as provided by the caller. */
    @ApplicationScoped
    class EntryPoint {
        @Inject
        CompareWorkflow workflow;
    }
}
