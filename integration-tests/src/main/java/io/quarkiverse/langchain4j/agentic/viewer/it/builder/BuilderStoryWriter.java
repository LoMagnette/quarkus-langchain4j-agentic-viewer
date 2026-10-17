package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryWriter;

/**
 * Builder system exposed as a typed agent interface (a CDI bean of this type), whose sub-agents are the declarative
 * agent interfaces of {@link StoryWriter}. The interface has no agentic annotation, so it is no declarative agent: its
 * parameters go to the scope by their {@code @V} names.
 */
public interface BuilderStoryWriter {

    String write(@V("bio") String bio);

    @ApplicationScoped
    class Producer {

        @Produces
        @ApplicationScoped
        BuilderStoryWriter storyWriter(StoryWriter.IntroWriter introWriter, StoryWriter.AdventureWriter adventureWriter) {
            return AgenticServices.sequenceBuilder(BuilderStoryWriter.class)
                    .name("write")
                    .description("Writes a short story about a person, built with the builder API")
                    .subAgents(AgentBeans.unwrap(introWriter, adventureWriter))
                    .output(scope -> scope.readState("intro", "") + "\n\n" + scope.readState("adventure", ""))
                    .build();
        }
    }
}
