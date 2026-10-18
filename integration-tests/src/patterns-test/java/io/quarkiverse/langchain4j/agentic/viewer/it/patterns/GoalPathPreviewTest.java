package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.PlannerTypeDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.GoapAdapter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.PatternAdapters;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * previewGoalPath predicts the agents a GOAP planner calls from the keys it starts with, by the planner's own search;
 * a run computes the same path (T4.3).
 */
@QuarkusTest
class GoalPathPreviewTest {

    private static AgenticSystem system;
    private static RunManager runManager;
    private static AgenticViewerJsonRpcService service;

    @BeforeAll
    static void setUp() throws Exception {
        File samples = new File(TripGoalPlanner.class.getResource("TripGoalPlanner.class").toURI()).getParentFile();
        Index index = Index.of(samples);
        List<AgenticSystem> systems = new TopologyBuilder(PlannerTypeDetector.of(index, GoalPathPreviewTest.class.getClassLoader()))
                .build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(index)));
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        systems.forEach(registry::register);
        PatternAdapters adapters = new PatternAdapters(List.of(new GoapAdapter()));
        runManager = new RunManager(registry::find, new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager, adapters));
        service = new AgenticViewerJsonRpcService(registry, runManager, adapters);
        system = systems.stream().filter(s -> s.name().equals("TripGoalPlanner")).findFirst().orElseThrow();
    }

    @AfterAll
    static void shutDown() {
        runManager.shutdown();
    }

    private static List<String> preview(String... keys) {
        Map<String, List<String>> paths = service.previewGoalPath(system.id(), List.of(keys));
        assertEquals(List.of(system.id()), List.copyOf(paths.keySet()), "one GOAP planner: the root");
        return paths.get(system.id()).stream().map(id -> id.substring(id.lastIndexOf('/') + 1)).toList();
    }

    @Test
    void pathFromTheRunInputs() {
        assertEquals(List.of("HotelFinder", "WeatherForecaster", "ActivityPlanner", "ItineraryBuilder"), preview("city", "budget"));
    }

    @Test
    void knownKeysShortenThePath() {
        // the planner's search orders what is left (both orders reach the goal)
        assertEquals(List.of("ActivityPlanner", "HotelFinder", "ItineraryBuilder"), preview("city", "budget", "forecast"));
        assertEquals(List.of("ItineraryBuilder"), preview("hotel", "activities"));
    }

    @Test
    void noPathWithoutTheNeededInputs() {
        assertEquals(List.of(), preview("budget"));
    }

    @Test
    void theRunTakesThePreviewedPath() throws Exception {
        Run run = runManager.run(runManager.startRun(system.id(), Map.of("city", "Lisbon", "budget", "medium"))).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
        List<PlannerDecision> decisions = run.events().stream().filter(PlannerDecision.class::isInstance)
                .map(PlannerDecision.class::cast).toList();
        List<String> previewed = service.previewGoalPath(system.id(), List.of("city", "budget")).get(system.id());
        assertEquals(previewed, decisions.get(0).extras().get("path"), "path computed at the first decision");
        assertEquals("itinerary", decisions.get(0).extras().get("goal"));
        assertEquals(previewed, decisions.stream().flatMap(d -> d.selectedNodeIds().stream()).toList(), "path walked");
    }
}
