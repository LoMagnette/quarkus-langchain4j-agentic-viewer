package io.quarkiverse.langchain4j.agentic.viewer.test.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;

/**
 * Stopping the application or a hot reload (RunManager.shutdown) ends the open runs and leaves no thread behind (T6.2).
 */
class RunShutdownTest {

    private static final String SYSTEM = "com.example.Slow#run";
    private static final AgenticSystem SLOW = new AgenticSystem(SYSTEM, "Slow", SYSTEM, SystemOrigin.DECLARATIVE,
            new SourceRef("com.example.Slow", "run", null), List.of(),
            new AgentNode(SYSTEM, "run", NodeKind.AI_AGENT, null, List.of(), null, false, false, List.of(), null, null, null,
                    null));

    private static long viewerThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().startsWith("agentic-viewer-")).count();
    }

    @Test
    void openRunsEndCancelledAndTheirStreamsComplete() throws Exception {
        long before = viewerThreads();
        CountDownLatch running = new CountDownLatch(1);
        RunManager manager = new RunManager(id -> SYSTEM.equals(id) ? Optional.of(SLOW) : Optional.empty(), (system, inputs) -> {
            running.countDown();
            Thread.sleep(60_000); // a model call that would take long: interrupted by the shutdown
            return "late";
        });
        String runId = manager.startRun(SYSTEM, null);
        Run run = manager.run(runId).orElseThrow();
        assertTrue(running.await(5, TimeUnit.SECONDS));
        AssertSubscriber<ExecutionEvent> stream = manager.stream(runId).subscribe()
                .withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        stream.awaitItems(1, Duration.ofSeconds(5));

        manager.shutdown();

        stream.awaitCompletion(Duration.ofSeconds(5));
        ExecutionEvent last = stream.getItems().get(stream.getItems().size() - 1);
        assertTrue(last instanceof RunFailed, () -> stream.getItems().toString());
        assertEquals("Application stopped or reloaded", ((RunFailed) last).error().message());
        assertEquals(RunStatus.CANCELLED, run.status());
        for (int i = 0; i < 50 && viewerThreads() > before; i++) {
            Thread.sleep(100);
        }
        assertEquals(before, viewerThreads(), "run and event threads stopped");
    }
}
