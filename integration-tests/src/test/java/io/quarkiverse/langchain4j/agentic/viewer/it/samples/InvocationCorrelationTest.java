package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
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
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Invocations are correlated within their own run: overlapping runs of a parallel system, the agents of a mapper item
 * that is a workflow, and a run following a cancelled one on the same thread.
 */
@QuarkusTest
class InvocationCorrelationTest {

    private static RunManager runManager;
    private static AgenticSystem twinSearch;
    private static AgenticSystem phraseSteps;
    private static AgenticSystem memoryOrder;

    @BeforeAll
    static void attach() throws Exception {
        List<AgenticSystem> systems = new TopologyBuilder().build(new RootSystemDetector().detect(
                new AgenticAnnotationScanner().scan(Index.of(TwinSearch.class, TwinSearch.LeftSearch.class,
                        TwinSearch.RightSearch.class, PhraseSteps.class, PhraseSteps.Steps.class, PhraseSteps.Upper.class,
                        PhraseSteps.Exclaim.class, MemoryOrder.class, MemoryOrder.ItemFinder.class,
                        MemoryOrder.ItemApproval.class, MemoryOrder.OrderWriter.class))));
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        systems.forEach(registry::register);
        runManager = new RunManager(registry::find, new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager));
        twinSearch = system(systems, "TwinSearch");
        phraseSteps = system(systems, "PhraseSteps");
        memoryOrder = system(systems, "MemoryOrder");
    }

    @AfterAll
    static void shutDown() {
        runManager.shutdown();
    }

    private static AgenticSystem system(List<AgenticSystem> systems, String name) {
        return systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private static Run ended(String runId) throws Exception {
        Run run = runManager.run(runId).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        return run;
    }

    private static List<AgentStarted> starts(Run run) {
        return run.events().stream().filter(AgentStarted.class::isInstance).map(AgentStarted.class::cast).toList();
    }

    private static AgentStarted start(Run run, String nodeId) {
        return starts(run).stream().filter(e -> e.nodeId().equals(nodeId)).findFirst()
                .orElseThrow(() -> new AssertionError("no start of " + nodeId + ": " + run.events()));
    }

    @Test
    void branchesOfOverlappingRunsStayInTheirRun() throws Exception {
        TwinSearch.Gate.holdUntil(4); // both branches of both runs run at the same time
        String first = runManager.startRun(twinSearch.id(), Map.of("topic", "one"));
        String second = runManager.startRun(twinSearch.id(), Map.of("topic", "two"));
        for (String runId : List.of(first, second)) {
            Run run = ended(runId);
            assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
            String root = start(run, twinSearch.id()).invocationId();
            for (String branch : List.of("/LeftSearch", "/RightSearch")) {
                assertEquals(root, start(run, twinSearch.id() + branch).parentInvocationId(),
                        () -> branch + " of " + runId + ": " + run.events());
            }
        }
    }

    @Test
    void theAgentsOfAMapperItemThatIsAWorkflowRunUnderTheirItem() throws Exception {
        Run run = ended(runManager.startRun(phraseSteps.id(), Map.of("phrases", List.of("a", "b", "c"))));
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
        // (the items share the scope, so their workflow keys overwrite each other: langchain4j's business, not ours)

        String steps = phraseSteps.id() + "/Steps";
        Map<String, AgentStarted> items = starts(run).stream().filter(e -> e.nodeId().equals(steps))
                .collect(Collectors.toMap(AgentStarted::invocationId, e -> e));
        assertEquals(Set.of(0, 1, 2), items.values().stream().map(AgentStarted::itemIndex).collect(Collectors.toSet()));
        List<AgentStarted> children = new ArrayList<>();
        children.addAll(starts(run).stream().filter(e -> e.nodeId().equals(steps + "/Upper")).toList());
        children.addAll(starts(run).stream().filter(e -> e.nodeId().equals(steps + "/Exclaim")).toList());
        assertEquals(6, children.size(), () -> run.events().toString());
        for (AgentStarted child : children) {
            AgentStarted item = items.get(child.parentInvocationId());
            assertNotNull(item, () -> "no item parent for " + child);
            assertEquals(item.itemIndex(), child.itemIndex(), () -> child.toString());
        }
    }

    @Test
    void aRunAfterACancelledRunStartsAtTheRoot() throws Exception {
        String cancelledId = runManager.startRun(memoryOrder.id(), Map.of("request", "a lamp"));
        awaitHumanStep(cancelledId);
        assertTrue(runManager.cancel(cancelledId));
        // the cancelled run's callbacks come after its end: its invocations are never closed on its thread
        awaitIdleRunThreads();

        String nextId = runManager.startRun(memoryOrder.id(), Map.of("request", "a desk"));
        awaitHumanStep(nextId);
        assertTrue(runManager.provideHumanInput(nextId, "yes"));
        Run next = ended(nextId);
        assertEquals(RunStatus.SUCCEEDED, next.status(), () -> next.events().toString());
        AgentStarted root = starts(next).get(0);
        assertEquals(memoryOrder.id(), root.nodeId(), () -> next.events().toString());
        assertNull(root.parentInvocationId());
        assertEquals(root.invocationId(), start(next, memoryOrder.id() + "/ItemFinder").parentInvocationId());
    }

    private static void awaitHumanStep(String runId) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            if (runManager.run(runId).orElseThrow().events().stream().anyMatch(AwaitingHumanInput.class::isInstance)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no AwaitingHumanInput: " + runManager.run(runId).orElseThrow().events());
    }

    /** Until no run thread executes agents any more (back in the pool, where the next run may reuse it). */
    private static void awaitIdleRunThreads() throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            boolean busy = Thread.getAllStackTraces().entrySet().stream()
                    .filter(e -> e.getKey().getName().startsWith("agentic-viewer-run-"))
                    .anyMatch(e -> Arrays.stream(e.getValue()).anyMatch(f -> f.getClassName().startsWith("dev.langchain4j")));
            if (!busy) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("a run thread is still busy");
    }
}
