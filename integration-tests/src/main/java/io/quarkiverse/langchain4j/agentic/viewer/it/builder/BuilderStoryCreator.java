package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryCreator;

/**
 * Builder system produced as a CDI bean ({@link UntypedAgent}): the builder twin of the declarative
 * {@link StoryCreator}, built from the same agent interfaces (a loop nested in a sequence). Its exit condition is a
 * lambda; everything else matches the declarative graph.
 * Its AI agents are its own, built from the interfaces ({@link Agents}).
 */
@ApplicationScoped
public class BuilderStoryCreator {

    public static final String NAME = "builderStoryCreator";
    public static final String LOOP_NAME = "builderStyleReviewLoop";

    /** A builder system of its own, and a sub-agent of the story creator: the Dev UI only lists the latter. */
    @Produces
    @ApplicationScoped
    @Named(LOOP_NAME)
    UntypedAgent styleReviewLoop(ChatModel chatModel) {
        return AgenticServices.loopBuilder()
                .name("polish")
                .description("Polishes the story until its style score is at least 0.8")
                .subAgents(Agents.fresh(chatModel, StoryCreator.StyleScorer.class),
                        Agents.fresh(chatModel, StoryCreator.StyleEditor.class))
                .maxIterations(5)
                .testExitAtLoopEnd(true)
                .exitCondition(scope -> scope.readState("score", 0.0) >= 0.8)
                .outputKey("story")
                .build();
    }

    @Produces
    @ApplicationScoped
    @Named(NAME)
    UntypedAgent storyCreator(ChatModel chatModel, @Named(LOOP_NAME) UntypedAgent styleReviewLoop) {
        return AgenticServices.sequenceBuilder()
                .name("create")
                .description("Drafts a story, then polishes its style")
                .subAgents(Agents.fresh(chatModel, StoryCreator.Drafter.class), Agents.unwrap(styleReviewLoop))
                .outputKey("story")
                .build();
    }
}
