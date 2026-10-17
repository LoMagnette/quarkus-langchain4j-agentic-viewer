package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.UntypedAgent;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Every builder and mixed-style sample runs on the scripted fake model and gives the same results as its declarative
 * counterpart (DeclarativeSamplesTest).
 */
@QuarkusTest
class BuilderSamplesTest {

    @Inject
    ScriptedChatModel model;

    @Inject
    @Named(BuilderStoryCreator.NAME)
    UntypedAgent builderStoryCreator;

    @Inject
    BuilderStoryWriter builderStoryWriter;

    @Inject
    ResearchService researchService;

    @Inject
    BlurbedStory blurbedStory;

    @BeforeEach
    void reset() {
        model.clearRequests();
    }

    private long requestsContaining(String text) {
        return model.requests().stream().filter(request -> ScriptedChatModel.lastUserMessage(request).contains(text))
                .count();
    }

    /** CDI bean of type UntypedAgent; same loop iterations as the declarative StoryCreator. */
    @Test
    void producedUntypedAgentTwinOfStoryCreator() {
        assertEquals("A story about a lighthouse (draft 4)", builderStoryCreator.invoke(Map.of("topic", "a lighthouse")));
        assertEquals(1, requestsContaining("Draft a story about"));
        assertEquals(3, requestsContaining("Score the style of this story"), "loop iterations");
    }

    /** CDI bean of a typed interface, with declarative agents as sub-agents. */
    @Test
    void producedTypedAgentWithDeclarativeSubAgents() {
        assertEquals("Once upon a time, there was Ada, a curious engineer.\n\n"
                + "One day, a dragon knocked at the door and asked for help.",
                builderStoryWriter.write("Ada, a curious engineer"));
    }

    /** Not a bean: kept in a field of a service. */
    @Test
    void systemKeptInAField() {
        assertEquals("Web: 3 articles about quantum computing\nDatabase: 2 records about quantum computing",
                researchService.research("quantum computing"));
    }

    /** Rebuilt on every request; only the expert whose lambda condition holds is called. */
    @Test
    void systemRebuiltPerRequest() {
        given().queryParam("request", "I hurt my leg while running").get("/builder/expert")
                .then().statusCode(200).body(equalTo("Rest, and see a doctor if it gets worse."));
        given().queryParam("request", "My laptop does not start").get("/builder/expert")
                .then().statusCode(200).body(equalTo("Restart it, then update the drivers."));
        assertEquals(1, requestsContaining("As a medical expert"));
        assertEquals(1, requestsContaining("As a technical expert"));
    }

    /** Declarative workflow whose first step delegates to the builder StoryCreator. */
    @Test
    void declarativeWorkflowWithABuilderSubAgent() {
        assertEquals("A tale you will not forget.", blurbedStory.create("a lighthouse"));
        assertEquals(3, requestsContaining("Score the style of this story"), "the builder system ran");
        assertEquals(1, requestsContaining("Write a one-line blurb for this story: A story about a lighthouse (draft 4)"));
    }
}
