package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeCreated;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeDestroyed;
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
 * A root with a memory id keeps its scope after the call: a scope belongs to a Dev UI run only while that run lasts,
 * and the scope of a memory id made up for a run does not outlive it.
 */
@QuarkusTest
class MemoryIdRunTest {

    @Inject
    MemoryOrder memoryOrder;

    private static AgenticViewerJsonRpcService service;
    private static RunManager runManager;
    private static AgenticSystem system;

    @BeforeAll
    static void service() throws Exception {
        List<AgenticSystem> systems = new TopologyBuilder().build(new RootSystemDetector().detect(
                new AgenticAnnotationScanner().scan(Index.of(MemoryOrder.class, MemoryOrder.ItemFinder.class,
                        MemoryOrder.ItemApproval.class, MemoryOrder.OrderWriter.class))));
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        systems.forEach(registry::register);
        runManager = new RunManager(registry::find, new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager));
        service = new AgenticViewerJsonRpcService(registry, runManager);
        system = systems.get(0);
    }

    @AfterAll
    static void shutDown() {
        runManager.shutdown();
    }

    @BeforeEach
    void reset() {
        HumanInput.reset();
    }

    private static Run run(String runId) {
        return runManager.run(runId).orElseThrow();
    }

    private static AwaitingHumanInput awaiting(String runId) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            Run run = run(runId);
            for (ExecutionEvent event : run.events()) {
                if (event instanceof AwaitingHumanInput waiting) {
                    return waiting;
                }
            }
            if (run.status() != RunStatus.RUNNING) {
                throw new AssertionError("the run ended without waiting: " + run.events());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no AwaitingHumanInput: " + run(runId).events());
    }

    /** A run of the system answered in the Dev UI, until it ends. */
    private static Run answeredRun(Map<String, Object> inputs, String answer) throws Exception {
        String runId = service.startRun(system.id(), inputs);
        awaiting(runId);
        assertTrue(service.provideHumanInput(runId, answer));
        Run run = run(runId);
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
        return run;
    }

    @Test
    void twoRunsWithTheSameMemoryIdAreTwoRuns() throws Exception {
        Run first = answeredRun(Map.of("customer", "u1", "request", "a lamp"), "first");
        Run second = answeredRun(Map.of("customer", "u1", "request", "a desk"), "second");

        assertEquals("Order of a desk: second", second.output());
        assertEquals(List.of(system.id(), system.id() + "/ItemFinder", system.id() + "/ItemApproval",
                system.id() + "/OrderWriter"), second.events().stream().filter(AgentStarted.class::isInstance)
                        .map(e -> ((AgentStarted) e).nodeId()).toList(), "the second run has its own events");
        assertEquals(4, first.events().stream().filter(AgentStarted.class::isInstance).count(),
                "nothing of the second run went to the first");
        assertTrue(HumanInput.prompts().isEmpty(), "both steps were answered in the Dev UI");
    }

    @Test
    void anApplicationCallReusingTheMemoryIdOfAnEndedRunIsNotIntercepted() throws Exception {
        answeredRun(Map.of("customer", "u2", "request", "a lamp"), "from the Dev UI");
        HumanInput.answerWith(prompt -> "from the application");

        String order = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> memoryOrder.order("u2", "a chair"));

        assertEquals("Order of a chair: from the application", order);
        assertEquals(List.of("Approve a chair"), HumanInput.prompts(), "the annotated method answered the step");
    }

    @Test
    void theScopeOfAMadeUpMemoryIdEndsWithTheRun() throws Exception {
        Run run = answeredRun(Map.of("request", "a lamp"), "yes");

        String memoryId = run.events().stream().filter(ScopeCreated.class::isInstance)
                .map(e -> ((ScopeCreated) e).memoryId()).findFirst().orElseThrow();
        assertEquals(36, memoryId.length(), "a fresh memory id");
        assertTrue(run.events().stream().anyMatch(e -> e instanceof ScopeDestroyed destroyed
                && destroyed.memoryId().equals(memoryId)), () -> run.events().toString());
        assertNull(((AgenticScopeAccess) ClientProxy.unwrap(memoryOrder)).getAgenticScope(memoryId),
                "the application keeps no scope of the run");
    }
}
