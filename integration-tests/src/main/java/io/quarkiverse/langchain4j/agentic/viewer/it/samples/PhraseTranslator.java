package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Parallel mapper: one translator instance per phrase, all running in parallel.
 */
public interface PhraseTranslator {

    @ParallelMapperAgent(description = "Translates every phrase to French in parallel", outputKey = "translations", subAgent = Translator.class, itemsProvider = "phrases")
    List<String> translateAll(@V("phrases") List<String> phrases);

    interface Translator {
        @UserMessage("Translate this phrase into French: {{phrase}}")
        @Agent(description = "Translates one phrase to French", outputKey = "translation")
        String translate(@V("phrase") String phrase);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {

        private static final Map<String, String> FRENCH = Map.of(
                "hello", "bonjour",
                "thank you", "merci",
                "good night", "bonne nuit");

        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Translate this phrase into French:"))
                    .reply(request -> {
                        String phrase = userMessageAfter(request, "French:");
                        return FRENCH.getOrDefault(phrase, "[" + phrase + "]");
                    });
        }
    }
}
