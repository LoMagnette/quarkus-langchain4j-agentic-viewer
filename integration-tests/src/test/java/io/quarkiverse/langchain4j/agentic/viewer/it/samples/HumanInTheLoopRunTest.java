package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeStateChanged;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The Dev UI answers the {@code @HumanInTheLoop} steps of its runs (T2.11): the step waits (AwaitingHumanInput) instead
 * of calling the annotated method, provideHumanInput answers it over JSON-RPC and the run goes on with the answer.
 */
@QuarkusTest
class HumanInTheLoopRunTest {

    @Inject
    ScriptedChatModel model;

    @Inject
    TripPlanner tripPlanner;

    private static AgenticViewerJsonRpcService service;
    private static RunManager runManager;
    private static AgenticSystem tripSystem;
    private static AgenticSystem copiesSystem;

    @BeforeAll
    static void service() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        List<AgenticSystem> systems = new ArrayList<>(new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples)))));
        systems.addAll(new TopologyBuilder().build(new RootSystemDetector().detect(new AgenticAnnotationScanner()
                .scan(Index.of(CopiesOrder.class, CopiesOrder.ProductFinder.class, CopiesOrder.CopiesQuestion.class,
                        CopiesOrder.OrderWriter.class)))));
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        systems.forEach(registry::register);
        runManager = new RunManager(registry::find, new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager));
        service = new AgenticViewerJsonRpcService(registry, runManager);
        tripSystem = systems.stream().filter(s -> s.name().equals("TripPlanner")).findFirst().orElseThrow();
        copiesSystem = systems.stream().filter(s -> s.name().equals("CopiesOrder")).findFirst().orElseThrow();
    }

    @AfterAll
    static void shutDown() {
        runManager.shutdown();
    }

    @BeforeEach
    void reset() {
        model.clearRequests();
        HumanInput.reset();
    }

    private static Run run(String runId) {
        return runManager.run(runId).orElseThrow();
    }

    /** Waits until the run's step waits for an answer. */
    private static AwaitingHumanInput awaiting(String runId) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            for (ExecutionEvent event : run(runId).events()) {
                if (event instanceof AwaitingHumanInput waiting) {
                    return waiting;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no AwaitingHumanInput: " + run(runId).events());
    }

    private static Run finished(String runId) throws Exception {
        Run run = run(runId);
        run.completion().get(30, TimeUnit.SECONDS);
        return run;
    }

    @Test
    void theDevUiApprovesTheDestination() throws Exception {
        String runId = service.startRun(tripSystem.id(), Map.of("wish", "a city by the sea"));
        AwaitingHumanInput waiting = awaiting(runId);
        String step = tripSystem.id() + "/TravelerApproval";
        assertEquals(step, waiting.nodeId());
        assertEquals("Asks the traveler to approve the destination", waiting.prompt());
        assertEquals("approval", waiting.expectedKey());
        assertEquals("java.lang.String", waiting.valueType());
        assertEquals(Map.of("destination", "Lisbon"), waiting.inputs());
        AgentStarted started = run(runId).events().stream().filter(AgentStarted.class::isInstance)
                .map(AgentStarted.class::cast).filter(e -> e.nodeId().equals(step)).findFirst().orElseThrow();
        assertEquals(started.invocationId(), waiting.invocationId());
        assertEquals(RunStatus.RUNNING, run(runId).status(), "the run waits");
        assertTrue(HumanInput.prompts().isEmpty(), "the annotated method is not called in a Dev UI run");

        assertTrue(service.provideHumanInput(runId, "Yes, sounds great"));

        Run run = finished(runId);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
        assertEquals("Day 1, 2 and 3 in Lisbon", run.output());
        List<ExecutionEvent> events = run.events();
        AgentFinished stepFinished = events.stream().filter(AgentFinished.class::isInstance).map(AgentFinished.class::cast)
                .filter(e -> e.nodeId().equals(step)).findFirst().orElseThrow();
        assertEquals("Yes, sounds great", stepFinished.output());
        assertTrue(events.indexOf(waiting) < events.indexOf(stepFinished));
        assertTrue(events.stream().anyMatch(e -> e instanceof ScopeStateChanged s && s.key().equals("approval")
                && s.value().equals("Yes, sounds great") && s.nodeId().equals(step)));
        assertTrue(events.get(events.size() - 1) instanceof RunFinished);
        assertFalse(service.provideHumanInput(runId, "again"), "nothing waits any more");
    }

    @Test
    void theDevUiRefusesTheDestination() throws Exception {
        String runId = service.startRun(tripSystem.id(), Map.of("wish", "a city by the sea"));
        awaiting(runId);
        assertTrue(service.provideHumanInput(runId, "No"));
        assertEquals("No itinerary: the traveler did not approve the destination.", finished(runId).output());
    }

    @Test
    void anIntAnswerIsReadAsJson() throws Exception {
        String runId = service.startRun(copiesSystem.id(), Map.of("request", "the manual"));
        AwaitingHumanInput waiting = awaiting(runId);
        assertEquals("int", waiting.valueType());
        assertEquals("How many copies?", waiting.prompt());
        assertEquals(Map.of("product", "the manual"), waiting.inputs());
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> service.provideHumanInput(runId, "three"));
        assertTrue(wrong.getMessage().startsWith("Expected int as JSON"), wrong.getMessage());
        assertEquals(RunStatus.RUNNING, run(runId).status(), "a wrong answer leaves the step waiting");

        assertTrue(service.provideHumanInput(runId, "3"));
        Run run = finished(runId);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
        assertEquals("Order: 3 copies of the manual", run.output());
    }

    @Test
    void cancellingARunStopsTheWaitingStep() throws Exception {
        String runId = service.startRun(tripSystem.id(), Map.of("wish", "a city by the sea"));
        awaiting(runId);
        assertTrue(service.cancelRun(runId));
        Run run = finished(runId);
        assertEquals(RunStatus.CANCELLED, run.status());
        for (int i = 0; i < 50 && service.provideHumanInput(runId, "late"); i++) {
            Thread.sleep(100);
        }
        assertFalse(service.provideHumanInput(runId, "late"), "the step stopped waiting");
        assertNull(run.output());
        assertTrue(HumanInput.prompts().isEmpty());
    }

    @Test
    void unknownRun() {
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> service.provideHumanInput("nope", "yes"));
        assertEquals("Unknown run: nope", unknown.getMessage());
    }

    @Test
    void callsOutsideTheDevUiStillAskTheAnnotatedMethod() {
        assertEquals("Day 1, 2 and 3 in Lisbon", tripPlanner.plan("a city by the sea"));
        assertEquals(List.of("Do you want to travel to Lisbon?"), HumanInput.prompts());
    }
}
