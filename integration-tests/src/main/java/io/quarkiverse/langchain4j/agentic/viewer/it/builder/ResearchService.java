package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ResearchGatherer;

/**
 * Builder system that is not a bean: built once by this service and kept in a private field. Only the service is a
 * bean; CDI never sees the system itself.
 */
@ApplicationScoped
public class ResearchService {

    @Inject
    ChatModel chatModel;

    private UntypedAgent quickResearch;

    @PostConstruct
    void build() {
        quickResearch = AgenticServices.parallelBuilder()
                .name("quickResearch")
                .description("Searches the web and the database in parallel")
                .subAgents(Agents.fresh(chatModel, ResearchGatherer.WebSearcher.class),
                        Agents.fresh(chatModel, ResearchGatherer.DatabaseSearcher.class))
                .output(scope -> "Web: " + scope.readState("webResults", "") + "\nDatabase: "
                        + scope.readState("dbResults", ""))
                .build();
    }

    public String research(String topic) {
        return String.valueOf(quickResearch.invoke(Map.of("topic", topic)));
    }
}
