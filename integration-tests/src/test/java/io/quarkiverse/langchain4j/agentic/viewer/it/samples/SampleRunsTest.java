package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Every typed sample system runs through RunManager with form-like inputs, as the Dev UI starts them, and gives the
 * same result as calling the bean directly (DeclarativeSamplesTest). The run manager is built here because the
 * runtime-dev beans only exist in dev mode.
 */
@QuarkusTest
class SampleRunsTest {

    @Inject
    ScriptedChatModel model;

    @Inject
    BankSupervisor.BankTool bankTool;

    private static RunManager runManager;
    private static Map<String, AgenticSystem> byName;

    @BeforeAll
    static void systems() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        List<AgenticSystem> systems = new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples))));
        byName = systems.stream().collect(Collectors.toMap(AgenticSystem::name, Function.identity()));
        runManager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(),
                new CdiRunInvoker());
    }

    @AfterAll
    static void shutDown() {
        runManager.shutdown();
    }

    @BeforeEach
    void reset() {
        model.clearRequests();
        bankTool.reset();
        HumanInput.reset();
    }

    /** Starts a run the way the run form does (JSON-like values by input name) and waits for its result. */
    private static Object run(String system, Map<String, Object> inputs) throws Exception {
        String runId = runManager.startRun(byName.get(system).id(), inputs);
        Run run = runManager.run(runId).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> system + ": " + run.events());
        List<ExecutionEvent> events = run.events();
        assertEquals(List.of("RunStarted", "RunFinished"), events.stream().map(ExecutionEvent::getType).toList());
        return run.output(); // the original value; events carry its JSON form
    }

    @Test
    void storyWriter() throws Exception {
        assertEquals("Once upon a time, there was Ada, a curious engineer.\n\n"
                + "One day, a dragon knocked at the door and asked for help.",
                run("StoryWriter", Map.of("bio", "Ada, a curious engineer")));
    }

    @Test
    void researchGatherer() throws Exception {
        assertEquals("Web: 3 articles about quantum computing\n"
                + "Database: 2 records about quantum computing\n"
                + "Archives: 1 old report about quantum computing",
                run("ResearchGatherer", Map.of("topic", "quantum computing")));
    }

    @Test
    void phraseTranslatorConvertsTheJsonList() throws Exception {
        assertEquals(List.of("bonjour", "merci", "bonne nuit"),
                run("PhraseTranslator", Map.of("phrases", List.of("hello", "thank you", "good night"))));
    }

    @Test
    void storyCreator() throws Exception {
        assertEquals("A story about a lighthouse (draft 4)", run("StoryCreator", Map.of("topic", "a lighthouse")));
    }

    @Test
    void expertRouter() throws Exception {
        assertEquals("Rest, and see a doctor if it gets worse.",
                run("ExpertRouter", Map.of("request", "I hurt my leg while running")));
    }

    @Test
    void bankSupervisor() throws Exception {
        assertEquals("Transferred 100 EUR (115.0 USD) from Mario to Georgios.",
                run("BankSupervisor", Map.of("request", BankSupervisor.Script.request())));
        assertEquals(885.0, bankTool.balance("Mario"));
    }

    @Test
    void draftReviewer() throws Exception {
        assertEquals("A polished draft about solar panels", run("DraftReviewer", Map.of("topic", "solar panels")));
    }

    @Test
    void tripPlanner() throws Exception {
        assertEquals("Day 1, 2 and 3 in Lisbon", run("TripPlanner", Map.of("wish", "a city by the sea")));
    }

    @Test
    void resilientStoryWriterWithAMissingInput() throws Exception {
        assertEquals("A tiny tale of pirates.", run("ResilientStoryWriter", Map.of("topic", "pirates")));
        assertEquals("A tiny tale of dragons and wizards.", run("ResilientStoryWriter", Map.of()));
    }

    /**
     * Runs execute on reused worker threads: each one gets its own request context, so request-scoped chat memory does
     * not leak from one run into the next (the bank agents' tool calls ended up in the drafter's prompt before).
     */
    @Test
    void runsDoNotShareChatMemory() throws Exception {
        for (int i = 0; i < 3; i++) {
            run("BankSupervisor", Map.of("request", BankSupervisor.Script.request()));
            bankTool.reset();
            assertEquals("A polished draft about solar panels", run("DraftReviewer", Map.of("topic", "solar panels")));
        }
    }

    @Test
    void conversionErrorFailsTheRunNamingTheInput() throws Exception {
        String runId = runManager.startRun(byName.get("PhraseTranslator").id(), Map.of("phrases", Map.of("not", "a list")));
        Run run = runManager.run(runId).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.FAILED, run.status());
        String message = ((RunFailed) run.events().get(1)).error().message();
        assertTrue(message.startsWith("Cannot convert input 'phrases' to java.util.List<java.lang.String>"), message);
    }
}
