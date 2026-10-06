package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Every declarative sample runs end to end on the scripted fake model, with a deterministic result.
 */
@QuarkusTest
class DeclarativeSamplesTest {

    @Inject
    ScriptedChatModel model;

    @Inject
    StoryWriter storyWriter;

    @Inject
    ResearchGatherer researchGatherer;

    @Inject
    PhraseTranslator phraseTranslator;

    @Inject
    StoryCreator storyCreator;

    @Inject
    ExpertRouter expertRouter;

    @Inject
    BankSupervisor bankSupervisor;

    @Inject
    BankSupervisor.BankTool bankTool;

    @Inject
    DraftReviewer draftReviewer;

    @Inject
    TripPlanner tripPlanner;

    @Inject
    ResilientStoryWriter resilientStoryWriter;

    @BeforeEach
    void reset() {
        model.clearRequests();
        bankTool.reset();
        HumanInput.reset();
    }

    @Test
    void sequenceWithOutput() {
        assertEquals("Once upon a time, there was Ada, a curious engineer.\n\n"
                + "One day, a dragon knocked at the door and asked for help.",
                storyWriter.writeStory("Ada, a curious engineer"));
    }

    @Test
    void parallelWithOutput() {
        assertEquals("Web: 3 articles about quantum computing\n"
                + "Database: 2 records about quantum computing\n"
                + "Archives: 1 old report about quantum computing",
                researchGatherer.gather("quantum computing"));
    }

    @Test
    void parallelMapper() {
        assertEquals(List.of("bonjour", "merci", "bonne nuit"),
                phraseTranslator.translateAll(List.of("hello", "thank you", "good night")));
        assertEquals(3, requestsContaining("Translate this phrase into French"));
    }

    @Test
    void loopNestedInSequenceRunsThreeIterations() {
        assertEquals("A story about a lighthouse (draft 4)", storyCreator.create("a lighthouse"));
        assertEquals(1, requestsContaining("Draft a story about"));
        assertEquals(3, requestsContaining("Score the style of this story"), "loop iterations");
        assertEquals(3, requestsContaining("Improve the style of this story"));
    }

    @Test
    void conditionalCallsOnlyTheMatchingExpert() {
        assertEquals("Rest, and see a doctor if it gets worse.", expertRouter.ask("I hurt my leg while running"));
        assertEquals(1, requestsContaining("As a medical expert"));
        assertEquals(0, requestsContaining("As a technical expert"));

        model.clearRequests();
        assertEquals("Restart it, then update the drivers.", expertRouter.ask("My laptop does not start"));
        assertEquals(0, requestsContaining("As a medical expert"));
        assertEquals(1, requestsContaining("As a technical expert"));
    }

    @Test
    void supervisorExchangesWithdrawsThenCredits() {
        assertEquals("Transferred 100 EUR (115.0 USD) from Mario to Georgios.",
                bankSupervisor.transact(BankSupervisor.Script.request()));

        List<String> agentPrompts = model.requests().stream()
                .map(ScriptedChatModel::lastUserMessage)
                .filter(message -> !message.contains("The last received response is"))
                .map(message -> message.lines().findFirst().orElse("")) // drop appended output format instructions
                .distinct()
                .toList();
        assertEquals(List.of(
                "Convert 100.0 EUR to USD.",
                "Withdraw 115.0 USD from Mario's account.",
                "Credit 115.0 USD to Georgios's account."), agentPrompts);
        assertEquals(885.0, bankTool.balance("Mario"));
        assertEquals(1115.0, bankTool.balance("Georgios"));
    }

    @Test
    void customPlannerRedraftsUntilApproved() {
        assertEquals("A polished draft about solar panels", draftReviewer.write("solar panels"));
        assertEquals(List.of("draft", "review", "draft", "review"), model.requests().stream()
                .map(request -> ScriptedChatModel.lastUserMessage(request).startsWith("Write a draft") ? "draft" : "review")
                .toList());
    }

    @Test
    void humanInTheLoopApproval() {
        assertEquals("Day 1, 2 and 3 in Lisbon", tripPlanner.plan("a city by the sea"));
        assertEquals(List.of("Do you want to travel to Lisbon?"), HumanInput.prompts());

        HumanInput.answerWith(prompt -> "No, thanks");
        assertEquals("No itinerary: the traveler did not approve the destination.",
                tripPlanner.plan("a city by the sea"));
    }

    @Test
    void errorHandlerRetriesWithDefaultTopic() {
        assertEquals("A tiny tale of pirates.", resilientStoryWriter.write("pirates"));
        assertEquals("A tiny tale of dragons and wizards.", resilientStoryWriter.write(null));
    }

    private long requestsContaining(String text) {
        return model.requests().stream()
                .filter(request -> ScriptedChatModel.lastUserMessage(request).contains(text))
                .count();
    }
}
