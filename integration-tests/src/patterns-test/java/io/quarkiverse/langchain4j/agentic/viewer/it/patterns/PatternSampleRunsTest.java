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
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The langchain4j-agentic-patterns samples (profile "patterns") run through RunManager with form-like inputs and give
 * the same results as PatternSamplesTest.
 */
@QuarkusTest
class PatternSampleRunsTest {

    private static List<AgenticSystem> systems;
    private static RunManager runManager;

    @BeforeAll
    static void systems() throws Exception {
        File samples = new File(TripGoalPlanner.class.getResource("TripGoalPlanner.class").toURI()).getParentFile();
        systems = new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples))));
        runManager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(),
                new CdiRunInvoker());
    }

    @AfterAll
    static void shutDown() {
        runManager.shutdown();
    }

    private static Object run(String name, Map<String, Object> inputs) throws Exception {
        AgenticSystem system = systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
        Run run = runManager.run(runManager.startRun(system.id(), inputs)).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> name + ": " + run.events());
        return run.output();
    }

    @Test
    void goalOrientedPlanner() throws Exception {
        assertEquals("Stay at Hotel Central in Lisbon, enjoy a boat tour and a picnic",
                run("TripGoalPlanner", Map.of("city", "Lisbon", "budget", "medium")));
    }

    @Test
    void peerToPeer() throws Exception {
        assertEquals("Hypothesis v3 about quantum batteries", run("ResearchTeam", Map.of("topic", "quantum batteries")));
    }

    @Test
    void blackboard() throws Exception {
        assertEquals("Bound the order cache to 10,000 entries",
                run("IncidentResponder", Map.of("incident", "Checkout fails since 10:00")));
    }

    @Test
    void bdi() throws Exception {
        assertEquals("Version 1.2.1 is out: it fixes the invoice export",
                run("ReleaseCaptain", Map.of("bugReport", "Invoice export fails")));
    }

    @Test
    void debate() throws Exception {
        assertEquals("Adopt one queue per region", run("DesignDebate", Map.of("proposal", "one queue")));
    }

    @Test
    void featureDelivery() throws Exception {
        // a Dev UI run without the Dev UI listener: the human step is called as is (HumanInput approves)
        assertEquals("Released: Exports now stream (review: APPROVE)",
                run("FeatureDelivery", Map.of("feature", "CSV export", "repository", "acme/billing")));
    }

    @Test
    void voting() throws Exception {
        assertEquals("APPROVE", run("ReviewPanel", Map.of("change", "Cache the invoices")));
    }
}
