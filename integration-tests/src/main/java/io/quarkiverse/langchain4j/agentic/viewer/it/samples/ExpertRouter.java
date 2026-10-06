package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Conditional: a router classifies the request, then only the matching expert answers.
 */
public interface ExpertRouter {

    enum RequestCategory {
        LEGAL,
        MEDICAL,
        TECHNICAL,
        UNKNOWN
    }

    @SequenceAgent(description = "Routes a request to the right expert", outputKey = "response", subAgents = {
            CategoryRouter.class, Experts.class })
    String ask(@V("request") String request);

    interface CategoryRouter {
        @UserMessage("Classify this request as LEGAL, MEDICAL, TECHNICAL or UNKNOWN: {{request}}")
        @Agent(description = "Classifies the request", outputKey = "category")
        RequestCategory classify(@V("request") String request);
    }

    interface Experts {
        @ConditionalAgent(description = "Asks the expert matching the category", outputKey = "response", subAgents = {
                MedicalExpert.class, TechnicalExpert.class, LegalExpert.class })
        String askExpert(@V("request") String request);

        @ActivationCondition(value = MedicalExpert.class, description = "category is medical")
        static boolean isMedical(@V("category") RequestCategory category) {
            return category == RequestCategory.MEDICAL;
        }

        @ActivationCondition(value = TechnicalExpert.class, description = "category is technical")
        static boolean isTechnical(@V("category") RequestCategory category) {
            return category == RequestCategory.TECHNICAL;
        }

        @ActivationCondition(value = LegalExpert.class, description = "category is legal")
        static boolean isLegal(@V("category") RequestCategory category) {
            return category == RequestCategory.LEGAL;
        }
    }

    interface MedicalExpert {
        @UserMessage("As a medical expert, answer: {{request}}")
        @Agent(description = "Answers medical questions", outputKey = "response")
        String medical(@V("request") String request);
    }

    interface TechnicalExpert {
        @UserMessage("As a technical expert, answer: {{request}}")
        @Agent(description = "Answers technical questions", outputKey = "response")
        String technical(@V("request") String request);
    }

    interface LegalExpert {
        @UserMessage("As a legal expert, answer: {{request}}")
        @Agent(description = "Answers legal questions", outputKey = "response")
        String legal(@V("request") String request);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Classify this request"))
                    .reply(request -> {
                        String text = userMessageAfter(request, "UNKNOWN:").toLowerCase();
                        if (text.contains("leg") || text.contains("fever")) {
                            return "MEDICAL";
                        }
                        if (text.contains("laptop") || text.contains("wifi")) {
                            return "TECHNICAL";
                        }
                        if (text.contains("contract") || text.contains("lease")) {
                            return "LEGAL";
                        }
                        return "UNKNOWN";
                    })
                    .when(userMessageContains("As a medical expert, answer:")).reply("Rest, and see a doctor if it gets worse.")
                    .when(userMessageContains("As a technical expert, answer:")).reply("Restart it, then update the drivers.")
                    .when(userMessageContains("As a legal expert, answer:")).reply("Read the termination clause first.");
        }
    }
}
