package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel.userMessageContains;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.bdi.BDIPlanner;
import dev.langchain4j.agentic.patterns.bdi.Desire;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ChatModelScript;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;

/**
 * BDI (belief-desire-intention): the planner pursues the achievable, unsatisfied desire of highest priority by calling
 * its agents in order. Fixing the bug comes first (achievable with a bug report, satisfied by a patch); the
 * announcement follows once there is a patch (notes, then the announcement).
 */
public interface ReleaseCaptain {

    @PlannerAgent(description = "Ships a bug fix release, pursuing its desires by priority", outputKey = "announcement", subAgents = {
            BugFixer.class, NotesWriter.class, Announcer.class })
    String release(@V("bugReport") String bugReport);

    @PlannerSupplier
    static Planner planner() {
        return new BDIPlanner(List.of(
                Desire.of("fix the bug", 10, "bugReport", "patch", BugFixer.class),
                Desire.of("announce the release", 5, "patch", "announcement", NotesWriter.class, Announcer.class)));
    }

    interface BugFixer {
        @UserMessage("Write a patch for: {{bugReport}}")
        @Agent(description = "Writes a patch", outputKey = "patch")
        String fix(@V("bugReport") String bugReport);
    }

    interface NotesWriter {
        @UserMessage("Write release notes for the patch: {{patch}}")
        @Agent(description = "Writes the release notes", outputKey = "releaseNotes")
        String writeNotes(@V("patch") String patch);
    }

    interface Announcer {
        @UserMessage("Announce the release with these notes: {{releaseNotes}}")
        @Agent(description = "Announces the release", outputKey = "announcement")
        String announce(@V("releaseNotes") String releaseNotes);
    }

    @ApplicationScoped
    class Script implements ChatModelScript {
        @Override
        public void contribute(ScriptedChatModel.Builder script) {
            script.when(userMessageContains("Write a patch for:")).reply("Null check in the invoice export")
                    .when(userMessageContains("Write release notes for the patch:")).reply("1.2.1 fixes the invoice export")
                    .when(userMessageContains("Announce the release with these notes:"))
                    .reply("Version 1.2.1 is out: it fixes the invoice export");
        }
    }
}
