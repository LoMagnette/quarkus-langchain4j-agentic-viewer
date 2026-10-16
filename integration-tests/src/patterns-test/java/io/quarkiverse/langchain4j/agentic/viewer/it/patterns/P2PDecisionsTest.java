package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.P2PAdapter;
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
 * Peer-to-peer planners tell, per decision, the invocations against the maximum and the agents whose inputs are
 * present, and why they stopped (T4.4).
 */
@QuarkusTest
class P2PDecisionsTest {

    private static List<AgenticSystem> systems;
    private static RunManager runManager;

    @BeforeAll
    static void setUp() throws Exception {
        File samples = new File(TripGoalPlanner.class.getResource("TripGoalPlanner.class").toURI()).getParentFile();
        Index index = Index.of(samples);
        Index testSystems = Index.of(P2PStopSystems.EndlessDebate.class, P2PStopSystems.LoneSearcher.class,
                ResearchTeam.LiteratureSearcher.class, ResearchTeam.HypothesisFormulator.class, ResearchTeam.Critic.class,
                ResearchTeam.Refiner.class);
        systems = new ArrayList<>();
        for (Index each : List.of(index, testSystems)) {
            systems.addAll(new TopologyBuilder(PlannerTypeDetector.of(each, P2PDecisionsTest.class.getClassLoader()))
                    .build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(each))));
        }
        runManager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(), new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager, new PatternAdapters(List.of(new P2PAdapter()))));
    }

    private static List<PlannerDecision> decisions(String name) throws Exception {
        AgenticSystem system = systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
        Run run = runManager.run(runManager.startRun(system.id(), Map.of("topic", "quantum batteries"))).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> name + ": " + run.events());
        return run.events().stream().filter(PlannerDecision.class::isInstance).map(PlannerDecision.class::cast).toList();
    }

    private static List<String> names(Object nodeIds) {
        return ((List<?>) nodeIds).stream().map(id -> id.toString().substring(id.toString().lastIndexOf('/') + 1)).toList();
    }

    @Test
    void researchTeamStopsOnItsExitCondition() throws Exception {
        List<PlannerDecision> decisions = decisions("ResearchTeam");
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 7), decisions.stream().map(d -> d.extras().get("invocations")).toList());
        decisions.forEach(d -> assertEquals(10, d.extras().get("maxInvocations")));
        assertEquals(List.of("LiteratureSearcher"), names(decisions.get(0).extras().get("ready")), "only the topic is known");
        PlannerDecision last = decisions.get(decisions.size() - 1);
        assertEquals(true, last.done());
        assertEquals(P2PAdapter.EXIT_CONDITION, last.extras().get("termination"));
    }

    @Test
    void maximumInvocations() throws Exception {
        PlannerDecision last = decisions("EndlessDebate").stream().reduce((a, b) -> b).orElseThrow();
        assertEquals(P2PAdapter.MAX_INVOCATIONS, last.extras().get("termination"));
        assertEquals(4, last.extras().get("invocations"));
        assertEquals(4, last.extras().get("maxInvocations"));
    }

    @Test
    void stableState() throws Exception {
        List<PlannerDecision> decisions = decisions("LoneSearcher");
        PlannerDecision last = decisions.get(decisions.size() - 1);
        assertEquals(P2PAdapter.STABLE_STATE, last.extras().get("termination"));
        assertEquals(1, last.extras().get("invocations"));
    }
}
