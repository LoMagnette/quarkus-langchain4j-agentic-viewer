package io.quarkiverse.langchain4j.agentic.viewer.test.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PayloadTruncator;
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

    private static RunManager manager(RunInvoker invoker, int maxConcurrentRuns) {
        return new RunManager(id -> SYSTEM.equals(id) ? Optional.of(ECHO) : Optional.empty(), invoker,
                PayloadTruncator.DEFAULT_MAX_SIZE, RunManager.DEFAULT_HISTORY_SIZE, maxConcurrentRuns);
    }

    /** Starts a run as soon as a run thread is free again. */
    private static String startWhenFree(RunManager manager) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            try {
                return manager.startRun(SYSTEM, Map.of("text", "again"));
            } catch (IllegalStateException e) {
                if (System.currentTimeMillis() > deadline) {
                    throw e;
                }
                Thread.sleep(20);
            }
        }
    }

    @Test
    void runsBeyondTheLimitAreRefusedUntilOneEnds() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        RunManager manager = manager((system, inputs) -> {
            release.await(5, TimeUnit.SECONDS);
            return "done";
        }, 2);
        String first = manager.startRun(SYSTEM, Map.of("text", "1"));
        manager.startRun(SYSTEM, Map.of("text", "2"));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> manager.startRun(SYSTEM, Map.of("text", "3")));
        assertTrue(refused.getMessage().contains("2 are running")
                && refused.getMessage().contains("quarkus.langchain4j.agentic.dev-ui.max-concurrent-runs"),
                refused::getMessage);
        assertEquals(2, manager.listRuns(SYSTEM).size(), "no run created for the refused one");

        release.countDown();
        manager.run(first).orElseThrow().completion().get(5, TimeUnit.SECONDS);
        Run again = manager.run(startWhenFree(manager)).orElseThrow();
        again.completion().get(5, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, again.status());
    }

    @Test
    void aCancelledRunKeepsItsThreadUntilItReturns() throws Exception {
        CountDownLatch working = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RunManager manager = manager((system, inputs) -> {
            working.countDown();
            long until = System.currentTimeMillis() + 5000;
            while (release.getCount() > 0 && System.currentTimeMillis() < until) {
                Thread.onSpinWait(); // a call ignoring the interruption
            }
            return "late";
        }, 1);
        String runId = manager.startRun(SYSTEM, Map.of("text", "1"));
        // once it runs (a run cancelled before its thread took it gives its thread back at once)
        assertTrue(working.await(5, TimeUnit.SECONDS));
        assertTrue(manager.cancel(runId));
        Run cancelled = manager.run(runId).orElseThrow();
        cancelled.completion().get(5, TimeUnit.SECONDS);
        assertEquals(RunStatus.CANCELLED, cancelled.status());
        assertThrows(IllegalStateException.class, () -> manager.startRun(SYSTEM, Map.of("text", "2")),
                "its thread still works");

        release.countDown();
        assertNotEquals(runId, startWhenFree(manager));
    }

    @Test
    void noRunStartsOnceShutDown() {
        RunManager manager = manager((system, inputs) -> "done");
        manager.shutdown();
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> manager.startRun(SYSTEM, Map.of("text", "late")));
        assertTrue(refused.getMessage().contains("stopping or reloading"), refused::getMessage);
        assertEquals(List.of(), manager.listRuns(SYSTEM));
    }

    @Test
    void aShutdownDuringTheStartLeavesNoRunBehind() {
        RunManager[] manager = new RunManager[1];
        manager[0] = manager((system, inputs) -> "done");
        // read while the run is being accepted: the manager shuts down meanwhile, its event is then rejected
        Map<String, Object> inputs = new java.util.AbstractMap<>() {
            @Override
            public java.util.Set<Entry<String, Object>> entrySet() {
                manager[0].shutdown();
                return Map.<String, Object> of("text", "racing").entrySet();
            }
        };
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> manager[0].startRun(SYSTEM, inputs));
        assertTrue(refused.getMessage().contains("stopping or reloading"), refused::getMessage);
        assertEquals(List.of(), manager[0].listRuns(SYSTEM), "no ghost run in the history");
    }

    @Test
    void aFailedStartGivesItsThreadBack() throws Exception {
        RunManager manager = manager((system, inputs) -> "done", 1);
        Map<String, Object> failing = new java.util.AbstractMap<>() {
            @Override
            public java.util.Set<Entry<String, Object>> entrySet() {
                throw new java.util.ConcurrentModificationException("changed while copied");
            }
        };
        assertThrows(java.util.ConcurrentModificationException.class, () -> manager.startRun(SYSTEM, failing));
        assertEquals(List.of(), manager.listRuns(SYSTEM));

        Run run = manager.run(manager.startRun(SYSTEM, Map.of("text", "next"))).orElseThrow();
        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), "the only run thread was given back");
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

    @Test
    void aCyclicOutputStillEndsTheRunWithRunFinished() throws Exception {
        Map<String, Object> output = new HashMap<>();
        output.put("me", output);
        RunManager manager = manager((system, inputs) -> output);
        try {
            Run run = manager.run(manager.startRun(SYSTEM, Map.of())).orElseThrow();
            run.completion().get(5, TimeUnit.SECONDS);
            assertEquals(RunStatus.SUCCEEDED, run.status());
            RunFinished finished = (RunFinished) run.events().get(run.events().size() - 1);
            assertTrue(String.valueOf(finished.output()).contains("not serializable to JSON"), finished::toString);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void aListenerThrowingAnErrorDoesNotStopTheOthers() throws Exception {
        RunManager manager = manager((system, inputs) -> "done");
        List<String> received = new CopyOnWriteArrayList<>();
        manager.addListener(event -> {
            throw new AssertionError("broken listener");
        });
        manager.addListener(event -> received.add(event.getType()));
        try {
            Run run = manager.run(manager.startRun(SYSTEM, Map.of())).orElseThrow();
            run.completion().get(5, TimeUnit.SECONDS);
            assertEquals(List.of("RunStarted", "RunFinished"), received);
        } finally {
            manager.shutdown();
        }
    }
}
