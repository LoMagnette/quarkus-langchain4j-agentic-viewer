package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryCreator;

/**
 * Builder system produced as a CDI bean ({@link UntypedAgent}): the builder twin of the declarative
 * {@link StoryCreator}, built from the same agent interfaces (a loop nested in a sequence). Its exit condition is a
 * lambda; everything else matches the declarative graph.
 * The sub-agents are the agent beans of the interfaces ({@link AgentBeans}).
 */
@ApplicationScoped
public class BuilderStoryCreator {

    public static final String NAME = "builderStoryCreator";

    @Produces
    @ApplicationScoped
    @Named(NAME)
    UntypedAgent storyCreator(StoryCreator.Drafter drafter, StoryCreator.StyleScorer scorer, StoryCreator.StyleEditor editor) {
        UntypedAgent styleReviewLoop = AgenticServices.loopBuilder()
                .name("polish")
                .description("Polishes the story until its style score is at least 0.8")
                .subAgents(AgentBeans.unwrap(scorer, editor))
                .maxIterations(5)
                .testExitAtLoopEnd(true)
                .exitCondition(scope -> scope.readState("score", 0.0) >= 0.8)
                .outputKey("story")
                .build();
        return AgenticServices.sequenceBuilder()
                .name("create")
                .description("Drafts a story, then polishes its style")
                .subAgents(AgentBeans.unwrap(drafter, styleReviewLoop))
                .outputKey("story")
                .build();
    }
}
