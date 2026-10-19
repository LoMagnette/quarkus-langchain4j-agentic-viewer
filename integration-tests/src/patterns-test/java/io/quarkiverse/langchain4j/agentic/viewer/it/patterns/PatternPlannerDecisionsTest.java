package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.HumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.BdiAdapter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.BlackboardAdapter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.DebateAdapter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.GoapAdapter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.P2PAdapter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.PatternAdapters;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.VotingAdapter;
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
 * The planners of langchain4j-agentic-patterns (GOAP, P2P, blackboard, BDI, debate, voting) report their decisions in Dev UI runs (T3.2)
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
        RootListeners.attach(systems, new DevUiAgentListener(runManager,
                new PatternAdapters(List.of(new GoapAdapter(), new P2PAdapter(), new BlackboardAdapter(),
                        new BdiAdapter(), new DebateAdapter(), new VotingAdapter()))));
    }

    @AfterAll
    static void shutDown() {
        runManager.shutdown();
    }

    private static Set<String> nodeIds(AgentNode node) {
        Set<String> ids = new HashSet<>();
        ids.add(node.id());
        node.children().forEach(child -> ids.addAll(nodeIds(child)));
        return ids;
    }

    private static List<PlannerDecision> assertDecisions(String name, Map<String, Object> inputs, String output,
            String pattern, List<String> expected) throws Exception {
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
        assertTrue(decisions.stream().allMatch(d -> pattern.equals(d.extras().get("pattern"))),
                name + ": decisions tagged by the pattern adapter " + decisions);
        Set<String> nodes = nodeIds(system.root());
        assertTrue(decisions.stream().allMatch(d -> nodes.contains(d.nodeId())
                && nodes.containsAll(d.selectedNodeIds())), name + ": node ids from the topology " + decisions);
        assertTrue(decisions.stream().anyMatch(d -> !d.selectedNodeIds().isEmpty()), name + ": agents chosen");
        return decisions;
    }

    @Test
    void goalOrientedPlanner() throws Exception {
        assertDecisions("TripGoalPlanner", Map.of("city", "Lisbon", "budget", "medium"),
                "Stay at Hotel Central in Lisbon, enjoy a boat tour and a picnic", "GOAP", List.of("call [HotelFinder]",
                        "call [WeatherForecaster]", "call [ActivityPlanner]", "call [ItineraryBuilder]", "done []"));
    }

    @Test
    void peerToPeer() throws Exception {
        assertDecisions("ResearchTeam", Map.of("topic", "quantum batteries"), "Hypothesis v3 about quantum batteries", "P2P",
                List.of("call [LiteratureSearcher]", "call [HypothesisFormulator]", "call [Critic]", "call [Refiner]",
                        "call [Critic]", "call [Refiner]", "call [Critic]", "done []"));
    }

    @Test
    void blackboard() throws Exception {
        assertDecisions("IncidentResponder", Map.of("incident", "Checkout fails since 10:00"),
                "Bound the order cache to 10,000 entries", "BLACKBOARD", List.of("call [MetricsAnalyzer]", "call [LogAnalyzer]",
                        "call [Diagnoser]", "call [FixProposer]", "done []"));
    }

    @Test
    void bdi() throws Exception {
        List<PlannerDecision> decisions = assertDecisions("ReleaseCaptain", Map.of("bugReport", "Invoice export fails"),
                "Version 1.2.1 is out: it fixes the invoice export", "BDI",
                List.of("call [BugFixer]", "call [NotesWriter]", "call [Announcer]", "done []"));
        // the invocations so far (public executionState): counted when the planner dispatches an agent
        assertEquals(List.of(1, 2, 3, 3), decisions.stream().map(d -> d.extras().get("invocations")).toList());
    }

    @Test
    void debate() throws Exception {
        List<PlannerDecision> decisions = assertDecisions("DesignDebate", Map.of("proposal", "one queue"),
                "Adopt one queue per region", "DEBATE", List.of("call [Advocate, Critic]", "call [Advocate, Critic]",
                        "call [Judge]", "doneWithResult []"));
        assertEquals(List.of(1, 2, 2, 2), decisions.stream().map(d -> d.extras().get("round")).toList());
        assertEquals(2, decisions.get(2).extras().get("messages"), "the judge sees both debaters' last messages");
    }

    @Test
    void voting() throws Exception {
        List<PlannerDecision> decisions = assertDecisions("ReviewPanel", Map.of("change", "Cache the invoices"), "APPROVE",
                "VOTING", List.of("call [StyleReviewer, SecurityReviewer, PerformanceReviewer]", "doneWithResult []"));
        assertEquals(3, decisions.get(0).extras().get("voters"));
        PlannerDecision done = decisions.get(1);
        assertEquals(List.of("APPROVE", "REJECT", "APPROVE"), done.extras().get("votes"));
        assertEquals("APPROVE", done.extras().get("result"));
        assertEquals(3, ((List<?>) done.extras().get("voted")).size());
    }

    @Test
    void plannersNestedInWorkflows() throws Exception {
        AgenticSystem system = systems.stream().filter(s -> s.name().equals("FeatureDelivery")).findFirst().orElseThrow();
        // the Dev UI answers the product owner's step
        runManager.addListener(event -> {
            if (event instanceof AwaitingHumanInput waiting) {
                runManager.provideHumanInput(waiting.runId(), HumanInput.DEFAULT_ANSWER);
            }
        });
        Run run = runManager.run(runManager.startRun(system.id(), Map.of("feature", "CSV export", "repository", "acme/billing")))
                .orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
        assertEquals("Released: Exports now stream (review: APPROVE)", run.output());

        List<PlannerDecision> decisions = run.events().stream().filter(PlannerDecision.class::isInstance)
                .map(PlannerDecision.class::cast).toList();
        Set<String> nodes = nodeIds(system.root());
        assertTrue(decisions.stream().allMatch(d -> nodes.contains(d.nodeId()) && nodes.containsAll(d.selectedNodeIds())),
                "node ids from the topology " + decisions);
        // per planner node: its pattern, then its decisions (the voting planner runs once per loop iteration)
        Map<String, List<String>> byPlanner = decisions.stream().collect(Collectors.groupingBy(
                d -> d.nodeId().substring(d.nodeId().lastIndexOf('/') + 1) + " " + d.extras().get("pattern"),
                LinkedHashMap::new, Collectors.mapping(d -> d.extras().get("action") + " " + d.selectedNodeIds().stream()
                        .map(id -> id.substring(id.lastIndexOf('/') + 1)).toList(), Collectors.toList())));
        String voters = "call [ReadabilityReviewer, SecurityAuditor, PerformanceAuditor]";
        assertEquals(Map.of(
                "SpecPlanner GOAP", List.of("call [RequirementsWriter]", "call [RiskAssessor]", "call [SpecWriter]", "done []"),
                "CodeReview VOTING", List.of(voters, "doneWithResult []", voters, "doneWithResult []"),
                "ReleaseTrain BDI", List.of("call [RolloutPlanner]", "call [ChangelogWriter]", "call [ReleaseAnnouncer]", "done []")),
                byPlanner);
        assertEquals(List.of("REJECT", "APPROVE"), decisions.stream().filter(d -> "doneWithResult".equals(d.extras().get("action")))
                .map(d -> d.extras().get("result")).toList(), "the reviewers reject version 1, approve version 2");
    }
}
