package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.PlannerTypeDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.BlackboardAdapter;
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
 * The Dev UI tells why a pattern planner stopped without calling the application's exit condition or goal again: a
 * predicate with side effects behaves the same in a Dev UI run as in an application call.
 */
@QuarkusTest
class PredicateCallsTest {

    @Inject
    CountingPredicates.CountingSearcher searcher;

    @Inject
    CountingPredicates.CountingBoard board;

    private static List<AgenticSystem> systems;
    private static RunManager runManager;

    @BeforeAll
    static void setUp() throws Exception {
        Index index = Index.of(CountingPredicates.CountingSearcher.class, CountingPredicates.CountingBoard.class,
                ResearchTeam.LiteratureSearcher.class, IncidentResponder.LogAnalyzer.class);
        systems = new ArrayList<>(new TopologyBuilder(PlannerTypeDetector.of(index, PredicateCallsTest.class.getClassLoader()))
                .build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(index))));
        runManager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(), new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager,
                new PatternAdapters(List.of(new P2PAdapter(), new BlackboardAdapter()))));
    }

    @AfterAll
    static void shutDown() {
        runManager.shutdown();
    }

    private static PlannerDecision lastDecision(String name, Map<String, Object> inputs) throws Exception {
        AgenticSystem system = systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
        Run run = runManager.run(runManager.startRun(system.id(), inputs)).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> name + ": " + run.events());
        return run.events().stream().filter(PlannerDecision.class::isInstance).map(PlannerDecision.class::cast)
                .reduce((a, b) -> b).orElseThrow();
    }

    @Test
    void theExitConditionIsCalledAsOftenAsWithoutTheDevUi() throws Exception {
        CountingPredicates.EXIT_CALLS.set(0);
        searcher.search("quantum batteries");
        int withoutDevUi = CountingPredicates.EXIT_CALLS.getAndSet(0);

        PlannerDecision last = lastDecision("CountingSearcher", Map.of("topic", "quantum batteries"));

        assertTrue(withoutDevUi > 0);
        assertEquals(withoutDevUi, CountingPredicates.EXIT_CALLS.get());
        assertEquals(P2PAdapter.STABLE_STATE, last.extras().get("termination"));
    }

    @Test
    void theGoalIsCalledAsOftenAsWithoutTheDevUi() throws Exception {
        CountingPredicates.GOAL_CALLS.set(0);
        board.watch("checkout errors");
        int withoutDevUi = CountingPredicates.GOAL_CALLS.getAndSet(0);

        PlannerDecision last = lastDecision("CountingBoard", Map.of("incident", "checkout errors"));

        assertTrue(withoutDevUi > 0);
        assertEquals(withoutDevUi, CountingPredicates.GOAL_CALLS.get());
        assertEquals(BlackboardAdapter.QUIESCENT, last.extras().get("termination"));
        assertEquals(false, last.extras().get("goalReached"));
    }
}
