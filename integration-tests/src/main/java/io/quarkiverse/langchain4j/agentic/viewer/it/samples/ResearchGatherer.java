package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageAfter;
import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * Parallel: three searches run at the same time, combined by an {@link Output} method.
 */
public interface ResearchGatherer {

    @ParallelAgent(description = "Gathers information from several sources in parallel", outputKey = "research", subAgents = {
            WebSearcher.class, DatabaseSearcher.class, ArchiveSearcher.class })
    String gather(@V("topic") String topic);

    @Output
    static String combine(@V("webResults") String webResults, @V("dbResults") String dbResults,
            @V("archiveResults") String archiveResults) {
        return "Web: " + webResults + "\nDatabase: " + dbResults + "\nArchives: " + archiveResults;
    }

    interface WebSearcher {
        @UserMessage("Search the web for: {{topic}}")
        @Agent(description = "Searches the web", outputKey = "webResults")
        String searchWeb(@V("topic") String topic);
    }

    interface DatabaseSearcher {
        @UserMessage("Search the company database for: {{topic}}")
        @Agent(description = "Searches the company database", outputKey = "dbResults")
        String searchDatabase(@V("topic") String topic);
    }

    interface ArchiveSearcher {
        @UserMessage("Search the archives for: {{topic}}")
        @Agent(description = "Searches the archives", outputKey = "archiveResults")
        String searchArchives(@V("topic") String topic);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Search the web for:"))
                    .reply(request -> "3 articles about " + userMessageAfter(request, "for:"))
                    .when(userMessageContains("Search the company database for:"))
                    .reply(request -> "2 records about " + userMessageAfter(request, "for:"))
                    .when(userMessageContains("Search the archives for:"))
                    .reply(request -> "1 old report about " + userMessageAfter(request, "for:"));
        }
    }
}
