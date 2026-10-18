package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorPlanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Supervisors and {@code @PlannerSupplier} planners report each decision of a Dev UI run as a PlannerDecision event
 * (T3.2), without changing what they do; the built-in workflows (sequence, loop...) report none.
 */
@QuarkusTest
class PlannerDecisionEventsTest {

    @Inject
    ScriptedChatModel model;

    @Inject
    BankSupervisor.BankTool bankTool;

    private static List<AgenticSystem> systems;
    private static RunManager runManager;

    @BeforeAll
    static void observeRoots() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        systems = new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples))));
        runManager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(),
                new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager));
        HumanInputAnswers.answerInTheDevUi(runManager);
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

    private static AgenticSystem system(String name) {
        return systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private static Run run(String name, Map<String, Object> inputs) throws Exception {
        Run run = runManager.run(runManager.startRun(system(name).id(), inputs)).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> name + ": " + run.events());
        return run;
    }

    /** "action: selected agents" per decision, agents as their last node id segment */
    private static List<String> decisions(Run run) {
        return run.events().stream().filter(PlannerDecision.class::isInstance).map(PlannerDecision.class::cast)
                .map(d -> d.extras().get("action") + (d.done() ? " (done)" : "") + ": "
                        + d.selectedNodeIds().stream().map(id -> id.substring(id.lastIndexOf('/') + 1)).toList())
                .toList();
    }

    @Test
    void supervisorDecisions() throws Exception {
        AgenticSystem system = system("BankSupervisor");
        Run run = run("BankSupervisor", Map.of("request", BankSupervisor.Script.request()));
        assertEquals("Transferred 100 EUR (115.0 USD) from Mario to Georgios.", run.output(), "behaviour unchanged");
        assertEquals(885.0, bankTool.balance("Mario"));
        assertEquals(List.of("call: [ExchangeAgent]", "call: [WithdrawAgent]", "call: [CreditAgent]",
                "doneWithResult (done): []"), decisions(run));

        List<ExecutionEvent> events = run.events();
        AgentStarted root = events.stream().filter(AgentStarted.class::isInstance).map(AgentStarted.class::cast)
                .findFirst().orElseThrow();
        List<PlannerDecision> decisions = events.stream().filter(PlannerDecision.class::isInstance)
                .map(PlannerDecision.class::cast).toList();
        assertTrue(decisions.stream().allMatch(d -> d.nodeId().equals(system.id()) && root.invocationId().equals(d.invocationId())),
                "decisions of the supervisor's invocation: " + decisions);
        assertEquals(system.id() + "/ExchangeAgent", decisions.get(0).selectedNodeIds().get(0));
        // each call is decided before the chosen agent starts
        for (PlannerDecision decision : decisions.subList(0, 3)) {
            String chosen = decision.selectedNodeIds().get(0);
            int started = events.indexOf(events.stream().filter(e -> e instanceof AgentStarted s && s.nodeId().equals(chosen))
                    .findFirst().orElseThrow());
            assertTrue(events.indexOf(decision) < started, chosen);
        }
    }

    @Test
    void customPlannerDecisions() throws Exception {
        Run run = run("DraftReviewer", Map.of("topic", "solar panels"));
        assertEquals("A polished draft about solar panels", run.output(), "behaviour unchanged");
        assertEquals(List.of("call: [Drafter]", "call: [Reviewer]", "call: [Drafter]", "call: [Reviewer]",
                "done (done): []"), decisions(run));
        assertTrue(run.events().stream().filter(PlannerDecision.class::isInstance)
                .noneMatch(e -> ((PlannerDecision) e).extras().containsKey("pattern")), "a custom planner has no pattern");
    }

    @Test
    void builtInWorkflowsReportNoDecision() throws Exception {
        for (var entry : Map.<String, Map<String, Object>> of(
                "StoryWriter", Map.of("bio", "Ada, a curious engineer"),
                "StoryCreator", Map.of("topic", "a lighthouse"),
                "ExpertRouter", Map.of("request", "I hurt my leg while running"),
                "PhraseTranslator", Map.of("phrases", List.of("hello", "thank you")),
                "TripPlanner", Map.of("wish", "a city by the sea")).entrySet()) {
            reset();
            assertEquals(List.of(), decisions(run(entry.getKey(), entry.getValue())), entry.getKey());
        }
    }

    @Inject
    BankSupervisor bankSupervisor;

    /**
     * langchain4j copies a workflow per scope (withAgenticScope) and takes the copy's default planner from the planner
     * supplier: the Dev UI wrapper must not show there (plannerType, the chat memory of a supervisor).
     */
    @Test
    void perScopeCopiesKeepTheOriginalPlanner() {
        Object root = ClientProxy.unwrap(bankSupervisor);
        AgentInstance copy = (AgentInstance) ((AgenticScopeOwner) root)
                .withAgenticScope(DefaultAgenticScope.ephemeralAgenticScope());

        assertEquals(SupervisorPlanner.class, copy.plannerType());
        assertEquals(SupervisorPlanner.class, ((AgentInstance) root).plannerType());
    }
}
