package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;

/**
 * The end of a run (cancel, shutdown) against what its threads still do: in the runs package, for the hooks the
 * listener calls.
 */
class RunEndEdgeCasesTest {

    private static final String SYSTEM = "com.example.Echo#run";
    private static final AgenticSystem ECHO = new AgenticSystem(SYSTEM, "Echo", SYSTEM, SystemOrigin.DECLARATIVE,
            new SourceRef("com.example.Echo", "run", null), List.of(),
            new AgentNode(SYSTEM, "run", NodeKind.AI_AGENT, null, null, null, false, false, null, null, null, null, null));

    private static RunManager manager(RunInvoker invoker) {
        return new RunManager(id -> SYSTEM.equals(id) ? Optional.of(ECHO) : Optional.empty(), invoker);
    }

    private static AwaitingHumanInput waiting(Run run) {
        return new AwaitingHumanInput(run.id(), System.currentTimeMillis(), SYSTEM + "/Step", null, "Approve?", "approval",
                "java.lang.String", Map.of());
    }

    @Test
    void aHumanStepReachedByACancelledRunFailsAtOnce() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch goOn = new CountDownLatch(1);
        CompletableFuture<String> step = new CompletableFuture<>();
        RunManager[] holder = new RunManager[1];
        RunManager manager = manager((system, inputs) -> {
            Run run = RunManager.currentRun().orElseThrow();
            started.countDown();
            while (goOn.getCount() > 0) { // a call ignoring interruption, e.g. a blocking model call
                try {
                    goOn.await();
                } catch (InterruptedException ignored) {
                    // keeps going
                }
            }
            try {
                step.complete(String.valueOf(holder[0].awaitHumanInput(run, waiting(run), String.class)));
            } catch (CancellationException e) {
                step.complete("cancelled");
            }
            return null;
        });
        holder[0] = manager;
        // the event thread is busy when the run is cancelled: the run is not marked ended yet
        CountDownLatch release = new CountDownLatch(1);
        manager.addListener(event -> {
            if (event instanceof RunFailed) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        try {
            String runId = manager.startRun(SYSTEM, Map.of());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(manager.cancel(runId));
            goOn.countDown();

            assertEquals("cancelled", step.get(3, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            manager.shutdown();
        }
    }

    @Test
    void cancellingRightAfterTheStartInterruptsTheRun() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        RunManager manager = manager((system, inputs) -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return null;
        });
        try {
            String runId = manager.startRun(SYSTEM, Map.of());
            assertTrue(manager.cancel(runId));
            Run run = manager.run(runId).orElseThrow();
            run.completion().get(5, TimeUnit.SECONDS);
            assertEquals(RunStatus.CANCELLED, run.status());
            // the run thread may not have started yet: then it never runs the system
            assertTrue(interrupted.await(2, TimeUnit.SECONDS) || interrupted.getCount() == 1);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void noEventIsRecordedAfterTheTerminalEvent() throws Exception {
        CountDownLatch finish = new CountDownLatch(1);
        RunManager manager = manager((system, inputs) -> {
            finish.await(5, TimeUnit.SECONDS);
            return "done";
        });
        try {
            Run run = manager.run(manager.startRun(SYSTEM, Map.of())).orElseThrow();
            // what a branch thread may emit right after the run's terminal event, before the run is marked ended
            manager.emit(run, new RunFinished(run.id(), System.currentTimeMillis(), "early", 1L));
            manager.emit(run, new AgentStarted(run.id(), System.currentTimeMillis(), SYSTEM, "inv-x", null, "late", null,
                    null, Map.of(), false));
            finish.countDown();
            run.completion().get(5, TimeUnit.SECONDS);

            List<ExecutionEvent> events = run.events();
            assertInstanceOf(RunFinished.class, events.get(events.size() - 1), events::toString);
            assertEquals(1, events.stream().filter(e -> e instanceof RunFinished || e instanceof RunFailed).count(),
                    events::toString);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void aFailingObserverNeverChangesWhatTheErrorHandlerDecided() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        ErrorRecoveryResult retry = ErrorRecoveryResult.retry();
        scope.withErrorHandler(context -> retry);
        ErrorHandlers.observe(scope, (context, result, failure) -> {
            throw new IllegalStateException("e.g. the run manager was shut down");
        });

        assertSame(retry, scope.handleError("agent", new AgentInvocationException("boom")));
    }

    @Test
    void aScopeReusedByAnotherRunReportsToThatRunOnly() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.withErrorHandler(context -> ErrorRecoveryResult.throwException());
        List<String> reports = new java.util.concurrent.CopyOnWriteArrayList<>();
        ErrorHandlers.observe(scope, (context, result, failure) -> reports.add("first"));
        ErrorHandlers.observe(scope, (context, result, failure) -> reports.add("second"));

        scope.handleError("agent", new AgentInvocationException("boom"));

        assertEquals(List.of("second"), reports);
    }
}
