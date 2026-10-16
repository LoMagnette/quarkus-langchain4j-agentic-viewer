package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.PlannerTypeDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.BlackboardAdapter;
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
 * Blackboard planners tell, per step, the candidates that could fire and the one picked, the strategy and whether the
 * goal holds, and why they stopped (T4.5).
 */
@QuarkusTest
class BlackboardDecisionsTest {

    private static List<AgenticSystem> systems;
    private static RunManager runManager;

    @BeforeAll
    static void setUp() throws Exception {
        File samples = new File(TripGoalPlanner.class.getResource("TripGoalPlanner.class").toURI()).getParentFile();
        systems = new ArrayList<>();
        for (Index index : List.of(Index.of(samples), Index.of(QuietBoard.class, IncidentResponder.LogAnalyzer.class))) {
            systems.addAll(new TopologyBuilder(PlannerTypeDetector.of(index, BlackboardDecisionsTest.class.getClassLoader()))
                    .build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(index))));
        }
        runManager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(), new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager, new PatternAdapters(List.of(new BlackboardAdapter()))));
    }

    private static List<PlannerDecision> decisions(String name) throws Exception {
        AgenticSystem system = systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
        Run run = runManager.run(runManager.startRun(system.id(), Map.of("incident", "Checkout fails since 10:00")))
                .orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> name + ": " + run.events());
        return run.events().stream().filter(PlannerDecision.class::isInstance).map(PlannerDecision.class::cast).toList();
    }

    private static String last(String nodeId) {
        return nodeId.substring(nodeId.lastIndexOf('/') + 1);
    }

    @Test
    void eachStepShowsTheCandidatesAndThePickedOne() throws Exception {
        List<PlannerDecision> decisions = decisions("IncidentResponder");
        List<String> steps = decisions.stream().filter(d -> !d.done()).map(d -> last(d.selectedNodeIds().get(0)) + " of "
                + ((List<?>) d.extras().get("candidates")).stream().map(id -> last(id.toString())).toList()).toList();
        assertEquals(List.of("MetricsAnalyzer of [LogAnalyzer, MetricsAnalyzer]", "LogAnalyzer of [LogAnalyzer]",
                "Diagnoser of [Diagnoser]", "FixProposer of [FixProposer]"), steps);
        decisions.stream().filter(d -> !d.done()).forEach(d -> {
            assertEquals("custom (IncidentResponder)", d.extras().get("strategy"));
            assertEquals(false, d.extras().get("goalReached"), "the fix is not on the board yet");
            assertTrue(d.extras().get("maxInvocations") instanceof Integer);
        });
        PlannerDecision done = decisions.get(decisions.size() - 1);
        assertEquals(true, done.extras().get("goalReached"));
        assertEquals(BlackboardAdapter.GOAL_REACHED, done.extras().get("termination"));
        assertEquals(4, done.extras().get("invocations"));
    }

    @Test
    void quiescentBoard() throws Exception {
        List<PlannerDecision> decisions = decisions("QuietBoard");
        PlannerDecision done = decisions.get(decisions.size() - 1);
        assertEquals(BlackboardAdapter.QUIESCENT, done.extras().get("termination"));
        assertEquals(1, done.extras().get("invocations"));
    }
}
