package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Test-only parallel mapper whose sub-agent is a workflow: the agents of each item run under that item.
 */
public interface PhraseSteps {

    @ParallelMapperAgent(outputKey = "shouted", subAgent = Steps.class, itemsProvider = "phrases")
    List<String> shoutAll(@V("phrases") List<String> phrases);

    interface Steps {
        @SequenceAgent(outputKey = "exclaimed", subAgents = { Upper.class, Exclaim.class })
        String steps(@V("phrase") String phrase);
    }

    interface Upper {
        @UserMessage("Upper case this step phrase: {{phrase}}")
        @Agent(description = "Upper cases the phrase", outputKey = "upper")
        String upper(@V("phrase") String phrase);
    }

    interface Exclaim {
        @UserMessage("Exclaim this step phrase: {{upper}}")
        @Agent(description = "Adds an exclamation mark", outputKey = "exclaimed")
        String exclaim(@V("upper") String upper);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Upper case this step phrase:"))
                    .reply(request -> userMessageAfter(request, "phrase:").toUpperCase())
                    .when(userMessageContains("Exclaim this step phrase:"))
                    .reply(request -> userMessageAfter(request, "phrase:") + "!");
        }
    }
}
