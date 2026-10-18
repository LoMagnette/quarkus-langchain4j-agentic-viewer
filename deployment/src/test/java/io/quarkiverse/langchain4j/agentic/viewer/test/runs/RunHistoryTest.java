package io.quarkiverse.langchain4j.agentic.viewer.test.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PayloadTruncator;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunSummary;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunTrace;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;

/**
 * Bounded run history, run traces and cancellation.
 */
class RunHistoryTest {

    private static AgenticSystem system(String id) {
        return new AgenticSystem(id, id, id, SystemOrigin.DECLARATIVE, new SourceRef(id, "run", null), List.of(),
                new AgentNode(id, "run", NodeKind.AI_AGENT, null, null, null, false, false, null, null, null, null, null));
    }

    /** Systems A and B; a run returns its "text" input, or blocks while its "block" latch is closed. */
    private static RunManager manager(int historySize) {
        return new RunManager(id -> Optional.of(system(id)), (system, inputs) -> {
            if (inputs.get("block") instanceof CountDownLatch latch) {
                latch.await(10, TimeUnit.SECONDS);
            }
            return inputs.get("text");
        }, PayloadTruncator.DEFAULT_MAX_SIZE, historySize);
    }

    private static Run runAndWait(RunManager manager, String system, String text) throws Exception {
        Run run = manager.run(manager.startRun(system, Map.of("text", text))).orElseThrow();
        run.completion().get(5, TimeUnit.SECONDS);
        return run;
    }

    @Test
    void historyKeepsTheLastRunsPerSystemNewestFirst() throws Exception {
        RunManager manager = manager(3);
        List<Run> runs = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            runs.add(runAndWait(manager, "A", "a" + i));
        }
        runAndWait(manager, "B", "b1");

        List<RunSummary> history = manager.listRuns("A");
        assertEquals(List.of(runs.get(4).id(), runs.get(3).id(), runs.get(2).id()),
                history.stream().map(RunSummary::runId).toList(), "the last 3, newest first");
        assertFalse(manager.run(runs.get(0).id()).isPresent(), "the oldest runs are dropped with their trace");
        assertEquals(1, manager.listRuns("B").size(), "per system");
        RunSummary last = history.get(0);
        assertEquals(RunStatus.SUCCEEDED, last.status());
        assertEquals(Map.of("text", "a5"), last.inputs());
        assertTrue(last.durationMillis() != null && last.durationMillis() >= 0);
    }

    @Test
    void runningRunsAreNotEvicted() throws Exception {
        RunManager manager = manager(1);
        CountDownLatch release = new CountDownLatch(1);
        Run blocked = manager.run(manager.startRun("A", Map.of("block", release, "text", "slow"))).orElseThrow();
        runAndWait(manager, "A", "fast");

        assertTrue(manager.run(blocked.id()).isPresent(), "still running: kept");
        assertNull(manager.listRuns("A").get(1).durationMillis(), "no duration while running");
        release.countDown();
        blocked.completion().get(5, TimeUnit.SECONDS);
        runAndWait(manager, "A", "next");
        assertEquals(1, manager.listRuns("A").size(), "ended runs evicted at the next start");
    }

    @Test
    void traceHasEveryEvent() throws Exception {
        RunManager manager = manager(5);
        Run run = runAndWait(manager, "A", "hi");
        RunTrace trace = manager.trace(run.id()).orElseThrow();
        assertEquals(run.id(), trace.summary().runId());
        assertEquals(List.of("RunStarted", "RunFinished"), trace.events().stream().map(ExecutionEvent::getType).toList());
        assertFalse(manager.trace("nope").isPresent());
    }

    @Test
    void cancelEndsARunningRunAndIgnoresItsLateResult() throws Exception {
        RunManager manager = manager(5);
        CountDownLatch never = new CountDownLatch(1);
        Run run = manager.run(manager.startRun("A", Map.of("block", never, "text", "late"))).orElseThrow();

        assertTrue(manager.cancel(run.id()));
        run.completion().get(5, TimeUnit.SECONDS);
        assertEquals(RunStatus.CANCELLED, run.status());
        RunFailed failed = (RunFailed) run.events().get(run.events().size() - 1);
        assertEquals("java.util.concurrent.CancellationException", failed.error().exceptionClass());
        assertEquals("Run cancelled", failed.error().message());

        Thread.sleep(200); // the interrupted invoker returns "late": ignored
        assertEquals(List.of("RunStarted", "RunFailed"), run.events().stream().map(ExecutionEvent::getType).toList());
        assertEquals(RunStatus.CANCELLED, manager.listRuns("A").get(0).status());
        assertFalse(manager.cancel(run.id()), "already ended");
        assertFalse(manager.cancel("nope"), "unknown");
    }

    @Test
    void runsStillRunningWhenTheHistoryIsFullAreDroppedOnceTheyEnd() throws Exception {
        RunManager manager = manager(2);
        try {
            CountDownLatch block = new CountDownLatch(1);
            List<Run> runs = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                runs.add(manager.run(manager.startRun("A", Map.of("block", block, "text", "t" + i))).orElseThrow());
            }
            assertEquals(6, manager.listRuns("A").size(), "running runs are kept");

            block.countDown();
            for (Run run : runs) {
                run.completion().get(5, TimeUnit.SECONDS);
            }

            // no other run starts: the history is bounded again once they ended
            for (int i = 0; i < 50 && manager.listRuns("A").size() > 2; i++) {
                Thread.sleep(20);
            }
            assertEquals(2, manager.listRuns("A").size());
            assertEquals(java.util.Optional.empty(), manager.run(runs.get(0).id()), "the oldest run is dropped");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void aRunKeepsItsInputsAsTheyWereWhenItStarted() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Object> seen = new java.util.concurrent.atomic.AtomicReference<>();
        RunManager manager = new RunManager(id -> Optional.of(system(id)), (system, inputs) -> {
            release.await(5, TimeUnit.SECONDS);
            seen.set(inputs.get("text"));
            return null;
        });
        try {
            Map<String, Object> inputs = new java.util.HashMap<>(Map.of("text", "before"));
            Run run = manager.run(manager.startRun("A", inputs)).orElseThrow();
            inputs.put("text", "after"); // the caller's map changes while the run waits
            inputs.put("extra", 1);
            release.countDown();
            run.completion().get(5, TimeUnit.SECONDS);

            assertEquals("before", seen.get());
            assertEquals(Map.of("text", "before"), run.inputs());
            assertEquals(Map.of("text", "before"),
                    ((io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunStarted) run.events().get(0)).inputs());
        } finally {
            manager.shutdown();
        }
    }
}
