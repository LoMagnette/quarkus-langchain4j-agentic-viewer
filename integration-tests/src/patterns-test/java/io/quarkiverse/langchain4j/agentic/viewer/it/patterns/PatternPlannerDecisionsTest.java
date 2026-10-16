package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The planners of langchain4j-agentic-patterns (GOAP, P2P, blackboard) report their decisions in Dev UI runs (T3.2)
 * and still give the same results.
 */
@QuarkusTest
class PatternPlannerDecisionsTest {

    private static List<AgenticSystem> systems;
    private static RunManager runManager;

    @BeforeAll
    static void observeRoots() throws Exception {
        File samples = new File(TripGoalPlanner.class.getResource("TripGoalPlanner.class").toURI()).getParentFile();
        systems = new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples))));
        runManager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(),
                new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager));
    }

    private static Set<String> nodeIds(AgentNode node) {
        Set<String> ids = new HashSet<>();
        ids.add(node.id());
        node.children().forEach(child -> ids.addAll(nodeIds(child)));
        return ids;
    }

    private static void assertDecisions(String name, Map<String, Object> inputs, String output, List<String> expected)
            throws Exception {
        AgenticSystem system = systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
        Run run = runManager.run(runManager.startRun(system.id(), inputs)).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> name + ": " + run.events());
        assertEquals(output, run.output(), name + ": behaviour unchanged");
        List<PlannerDecision> decisions = run.events().stream().filter(PlannerDecision.class::isInstance)
                .map(PlannerDecision.class::cast).toList();
        assertEquals(expected, decisions.stream().map(d -> d.extras().get("action") + " "
                + d.selectedNodeIds().stream().map(id -> id.substring(id.lastIndexOf('/') + 1)).toList()).toList(), name);
        assertTrue(decisions.get(decisions.size() - 1).done(), name + ": ends with a done decision");
        Set<String> nodes = nodeIds(system.root());
        assertTrue(decisions.stream().allMatch(d -> nodes.contains(d.nodeId())
                && nodes.containsAll(d.selectedNodeIds())), name + ": node ids from the topology " + decisions);
        assertTrue(decisions.stream().anyMatch(d -> !d.selectedNodeIds().isEmpty()), name + ": agents chosen");
    }

    @Test
    void goalOrientedPlanner() throws Exception {
        assertDecisions("TripGoalPlanner", Map.of("city", "Lisbon", "budget", "medium"),
                "Stay at Hotel Central in Lisbon, enjoy a boat tour and a picnic", List.of("call [HotelFinder]",
                        "call [WeatherForecaster]", "call [ActivityPlanner]", "call [ItineraryBuilder]", "done []"));
    }

    @Test
    void peerToPeer() throws Exception {
        assertDecisions("ResearchTeam", Map.of("topic", "quantum batteries"), "Hypothesis v3 about quantum batteries",
                List.of("call [LiteratureSearcher]", "call [HypothesisFormulator]", "call [Critic]", "call [Refiner]",
                        "call [Critic]", "call [Refiner]", "call [Critic]", "done []"));
    }

    @Test
    void blackboard() throws Exception {
        assertDecisions("IncidentResponder", Map.of("incident", "Checkout fails since 10:00"),
                "Bound the order cache to 10,000 entries", List.of("call [MetricsAnalyzer]", "call [LogAnalyzer]",
                        "call [Diagnoser]", "call [FixProposer]", "done []"));
    }
}
