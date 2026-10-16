package io.quarkiverse.langchain4j.agentic.viewer.test.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;

class RunManagerTest {

    private static final String SYSTEM = "com.example.Echo#run";
    private static final AgenticSystem ECHO = new AgenticSystem(SYSTEM, "Echo", SYSTEM, SystemOrigin.DECLARATIVE,
            new SourceRef("com.example.Echo", "run", null), List.of(),
            new AgentNode(SYSTEM, "run", NodeKind.AI_AGENT, null, null, null, false, false, null, null, null, null, null));

    private static RunManager manager(RunInvoker invoker) {
        return new RunManager(id -> SYSTEM.equals(id) ? Optional.of(ECHO) : Optional.empty(), invoker);
    }

    @Test
    void startRunReturnsBeforeTheRunEnds() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        List<String> threads = new CopyOnWriteArrayList<>();
        RunManager manager = manager((system, inputs) -> {
            threads.add(Thread.currentThread().getName());
            release.await(5, TimeUnit.SECONDS);
            return "echo " + inputs.get("text");
        });

        String runId = manager.startRun(SYSTEM, Map.of("text", "hi"));
        Run run = manager.run(runId).orElseThrow();
        assertEquals(RunStatus.RUNNING, run.status(), "startRun did not wait for the run");

        release.countDown();
        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status());
        assertEquals("echo hi", run.output());
        assertTrue(threads.get(0).startsWith("agentic-viewer-run-"), threads::toString);

        List<ExecutionEvent> events = run.events();
        assertEquals(List.of("RunStarted", "RunFinished"), events.stream().map(ExecutionEvent::getType).toList());
        RunStarted started = (RunStarted) events.get(0);
        assertEquals(SYSTEM, started.systemId());
        assertEquals(Map.of("text", "hi"), started.inputs());
        RunFinished finished = (RunFinished) events.get(1);
        assertEquals("echo hi", finished.output());
        assertTrue(finished.durationMillis() >= 0);
        assertTrue(events.stream().allMatch(e -> e.runId().equals(runId)));
    }

    @Test
    void failureEndsTheRunWithTheError() throws Exception {
        RunManager manager = manager((system, inputs) -> {
            throw new IllegalArgumentException("Cannot convert input 'count' to int");
        });
        Run run = manager.run(manager.startRun(SYSTEM, Map.of())).orElseThrow();
        run.completion().get(5, TimeUnit.SECONDS);

        assertEquals(RunStatus.FAILED, run.status());
        RunFailed failed = assertInstanceOf(RunFailed.class, run.events().get(1));
        assertEquals(IllegalArgumentException.class.getName(), failed.error().exceptionClass());
        assertEquals("Cannot convert input 'count' to int", failed.error().message());
    }

    @Test
    void concurrentRunsOfOneSystemAreTrackedSeparately() throws Exception {
        CountDownLatch bothRunning = new CountDownLatch(2);
        RunManager manager = manager((system, inputs) -> {
            bothRunning.countDown();
            assertTrue(bothRunning.await(5, TimeUnit.SECONDS), "the runs did not overlap");
            return inputs.get("text");
        });
        List<ExecutionEvent> seen = new CopyOnWriteArrayList<>();
        manager.addListener(seen::add);

        Run first = manager.run(manager.startRun(SYSTEM, Map.of("text", "one"))).orElseThrow();
        Run second = manager.run(manager.startRun(SYSTEM, Map.of("text", "two"))).orElseThrow();
        first.completion().get(5, TimeUnit.SECONDS);
        second.completion().get(5, TimeUnit.SECONDS);

        assertNotEquals(first.id(), second.id());
        assertEquals("one", first.output());
        assertEquals("two", second.output());
        assertTrue(first.events().stream().allMatch(e -> e.runId().equals(first.id())));
        assertTrue(second.events().stream().allMatch(e -> e.runId().equals(second.id())));
        assertEquals(4, seen.size(), "listeners receive the events of every run");
    }

    @Test
    void unknownSystem() {
        RunManager manager = manager((system, inputs) -> null);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> manager.startRun("com.example.Nope#run", Map.of()));
        assertEquals("Unknown agentic system: com.example.Nope#run", error.getMessage());
        assertFalse(manager.run("nope").isPresent());
    }
}
