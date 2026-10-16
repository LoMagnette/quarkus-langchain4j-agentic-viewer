package io.quarkiverse.langchain4j.agentic.viewer.test.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;

/**
 * streamRun: replay of the events already emitted, then the live ones, completion at the end of the run.
 */
class RunStreamTest {

    private static final String SYSTEM = "com.example.Steps#run";
    private static final AgenticSystem STEPS = new AgenticSystem(SYSTEM, "Steps", SYSTEM, SystemOrigin.DECLARATIVE,
            new SourceRef("com.example.Steps", "run", null), List.of(),
            new AgentNode(SYSTEM, "run", NodeKind.SEQUENCE, null, null, null, false, false, null, null, null, null, null));

    /** A fake system that runs until released. */
    private static RunManager manager(CountDownLatch release) {
        return new RunManager(id -> Optional.of(STEPS), (system, inputs) -> {
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return "done";
        });
    }

    private static List<String> types(List<ExecutionEvent> events) {
        return events.stream().map(ExecutionEvent::getType).toList();
    }

    @Test
    void subscriberJoiningDuringTheRunGetsTheReplayThenTheLiveEvents() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        RunManager manager = manager(release);
        Run run = manager.run(manager.startRun(SYSTEM, Map.of())).orElseThrow();
        manager.emit(run, new AgentStarted(run.id(), 1, SYSTEM, "inv-1", null, "run", null, Map.of(), false));

        AssertSubscriber<ExecutionEvent> subscriber = manager.stream(run.id())
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        subscriber.awaitItems(2, Duration.ofSeconds(5));
        assertEquals(List.of("RunStarted", "AgentStarted"), types(subscriber.getItems()), "replayed");

        manager.emit(run, new AgentFinished(run.id(), 2, SYSTEM, "inv-1", "x", null, 1));
        release.countDown();
        subscriber.awaitCompletion(Duration.ofSeconds(5));
        assertEquals(List.of("RunStarted", "AgentStarted", "AgentFinished", "RunFinished"), types(subscriber.getItems()),
                "then live, without gap nor duplicate, and complete at the end");
    }

    @Test
    void subscriberJoiningAfterTheEndGetsTheFullRun() throws Exception {
        CountDownLatch release = new CountDownLatch(0);
        RunManager manager = manager(release);
        Run run = manager.run(manager.startRun(SYSTEM, Map.of())).orElseThrow();
        run.completion().get(5, TimeUnit.SECONDS);

        AssertSubscriber<ExecutionEvent> subscriber = manager.stream(run.id())
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        subscriber.awaitCompletion(Duration.ofSeconds(5));
        assertEquals(List.of("RunStarted", "RunFinished"), types(subscriber.getItems()));
    }

    @Test
    void slowSubscriberDoesNotHoldTheRunNorTheOtherSubscribers() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        RunManager manager = manager(release);
        Run run = manager.run(manager.startRun(SYSTEM, Map.of())).orElseThrow();

        AssertSubscriber<ExecutionEvent> slow = manager.stream(run.id())
                .onItem().invoke(event -> sleep(300))
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        AssertSubscriber<ExecutionEvent> fast = manager.stream(run.id())
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        for (int i = 0; i < 5; i++) {
            manager.emit(run, new AgentStarted(run.id(), i, SYSTEM, "inv-" + i, null, "run", null, Map.of(), false));
        }
        release.countDown();

        long start = System.nanoTime();
        run.completion().get(5, TimeUnit.SECONDS);
        fast.awaitCompletion(Duration.ofSeconds(1));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMillis < 1000, "the run and the fast subscriber waited " + elapsedMillis + " ms for the slow one");
        assertEquals(7, fast.getItems().size());

        slow.awaitCompletion(Duration.ofSeconds(10));
        assertEquals(7, slow.getItems().size(), "the slow subscriber still gets everything");
    }

    @Test
    void unknownRunFails() {
        RunManager manager = manager(new CountDownLatch(0));
        AssertSubscriber<ExecutionEvent> subscriber = manager.stream("nope")
                .subscribe().withSubscriber(AssertSubscriber.create(1));
        subscriber.assertFailedWith(IllegalArgumentException.class, "Unknown run: nope");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
