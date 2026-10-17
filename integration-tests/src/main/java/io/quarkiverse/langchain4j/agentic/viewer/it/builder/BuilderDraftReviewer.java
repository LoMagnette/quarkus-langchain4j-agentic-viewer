package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.DraftReviewer;

/**
 * Builder system with a custom planner ({@code plannerBuilder}): the builder twin of the declarative
 * {@link DraftReviewer}, whose planner redrafts until the reviewer approves.
 */
@ApplicationScoped
public class BuilderDraftReviewer {

    public static final String NAME = "builderDraftReviewer";

    @Produces
    @ApplicationScoped
    @Named(NAME)
    UntypedAgent draftReviewer(ChatModel chatModel) {
        return AgenticServices.plannerBuilder()
                .name("write")
                .description("Drafts a text and reworks it until the reviewer approves")
                .subAgents(Agents.fresh(chatModel, DraftReviewer.Drafter.class),
                        Agents.fresh(chatModel, DraftReviewer.Reviewer.class))
                .planner(DraftReviewer.DraftReviewPlanner::new)
                .outputKey("draft")
                .build();
    }
}
