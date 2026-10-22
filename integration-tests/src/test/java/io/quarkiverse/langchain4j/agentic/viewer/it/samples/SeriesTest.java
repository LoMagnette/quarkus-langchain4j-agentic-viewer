package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PayloadTruncator;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.SeriesRun;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.SeriesTrace;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * A series (Agentic Test Runs page, TASK-98): one system run N times with the same inputs, one run after the other,
 * with per-run results, kept out of the run history.
 */
@QuarkusTest
class SeriesTest {

    private static AgenticSystemRegistry registry;
    /** Its human steps wait for the Dev UI. */
    private static RunManager manager;
    /** Answers every human step at once. */
    private static RunManager answering;
    private static AgenticViewerJsonRpcService service;

    @BeforeAll
    static void observe() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        List<AgenticSystem> systems = new ArrayList<>(scan(Index.of(samples)));
        systems.addAll(scan(Index.of(FailingFlow.class, FailingFlow.Exploder.class)));
        registry = new AgenticSystemRegistry();
        systems.forEach(registry::register);
        manager = new RunManager(registry::find, new CdiRunInvoker());
        answering = new RunManager(registry::find, new CdiRunInvoker());
        HumanInputAnswers.answerInTheDevUi(answering);
        RootListeners.attach(systems, new DevUiAgentListener(manager));
        RootListeners.attach(systems, new DevUiAgentListener(answering));
        service = new AgenticViewerJsonRpcService(registry, manager);
    }

    @AfterAll
    static void shutDown() {
        manager.shutdown();
        answering.shutdown();
    }

    @BeforeEach
    void reset() {
        HumanInput.reset();
    }

    private static List<AgenticSystem> scan(Index index) {
        return new TopologyBuilder().build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(index)));
    }

    private static String systemId(String name) {
        return registry.systems().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow().id();
    }

    private static SeriesTrace ended(RunManager runManager, String seriesId) throws Exception {
        for (int i = 0; i < 600; i++) {
            SeriesTrace trace = runManager.series(seriesId).orElseThrow();
            if (trace.status() != RunStatus.RUNNING) {
                return trace;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("series still running: " + seriesId);
    }

    @Test
    void aSeriesRunsTheSystemNTimesOneAfterTheOther() throws Exception {
        String storyWriter = systemId("StoryWriter");
        int historyBefore = manager.listRuns(storyWriter).size();

        SeriesTrace trace = ended(manager, service.startSeries(storyWriter, Map.of("bio", "Ada"), 3));

        assertEquals(RunStatus.SUCCEEDED, trace.status());
        assertEquals(3, trace.count());
        assertEquals(List.of(1, 2, 3), trace.runs().stream().map(SeriesRun::index).toList());
        String story = "Once upon a time, there was Ada.\n\nOne day, a dragon knocked at the door and asked for help.";
        for (SeriesRun run : trace.runs()) {
            assertEquals(RunStatus.SUCCEEDED, run.status());
            assertEquals(story, run.output());
            assertEquals(List.of(storyWriter, storyWriter + "/IntroWriter", storyWriter + "/AdventureWriter"), run.path());
            assertNotNull(run.durationMillis());
        }
        // one after the other: each run starts once the one before ended
        for (int i = 1; i < trace.runs().size(); i++) {
            Run previous = manager.run(trace.runs().get(i - 1).runId()).orElseThrow();
            long previousEnd = previous.events().stream().filter(RunFinished.class::isInstance)
                    .mapToLong(e -> e.timestamp()).findFirst().orElseThrow();
            assertTrue(manager.run(trace.runs().get(i).runId()).orElseThrow().startedAt() >= previousEnd);
        }
        assertEquals(historyBefore, manager.listRuns(storyWriter).size(), "out of the run history");
        assertEquals(trace.seriesId(), service.listSeries(storyWriter).get(0).seriesId());
        assertEquals(3, service.listSeries(storyWriter).get(0).started());
    }

    @Test
    void aSeriesCountsFrom1To50() {
        String storyWriter = systemId("StoryWriter");
        assertThrows(IllegalArgumentException.class, () -> service.startSeries(storyWriter, Map.of("bio", "Ada"), 0));
        assertThrows(IllegalArgumentException.class, () -> service.startSeries(storyWriter, Map.of("bio", "Ada"), 51));
        assertThrows(IllegalArgumentException.class, () -> service.startSeries("nope", Map.of(), 2));
        assertThrows(IllegalArgumentException.class, () -> service.getSeries("nope"));
        assertFalse(service.cancelSeries("nope"));
    }

    @Test
    void aStoppedSeriesStartsNoFurtherRun() throws Exception {
        String seriesId = manager.startSeries(systemId("TripPlanner"), Map.of("wish", "a city by the sea"), 5);
        // its first run waits for the human step (a Dev UI run)
        for (int i = 0; i < 300 && !waiting(seriesId); i++) {
            Thread.sleep(50);
        }
        assertTrue(waiting(seriesId), "the first run waits for its human step");

        assertTrue(service.cancelSeries(seriesId));

        SeriesTrace trace = ended(manager, seriesId);
        assertEquals(RunStatus.CANCELLED, trace.status());
        assertEquals(1, trace.runs().size());
        assertEquals(RunStatus.CANCELLED, manager.run(trace.runs().get(0).runId()).orElseThrow().completion()
                .get(30, TimeUnit.SECONDS).status());
        assertFalse(service.cancelSeries(seriesId), "nothing left to stop");
    }

    private static boolean waiting(String seriesId) {
        List<SeriesRun> runs = manager.series(seriesId).orElseThrow().runs();
        return !runs.isEmpty() && manager.run(runs.get(0).runId()).orElseThrow().events().stream()
                .anyMatch(AwaitingHumanInput.class::isInstance);
    }

    @Test
    void theHumanStepsOfASeriesAreAnsweredInTheDevUi() throws Exception {
        SeriesTrace trace = ended(answering, answering.startSeries(systemId("TripPlanner"),
                Map.of("wish", "a city by the sea"), 2));

        assertEquals(List.of("Day 1, 2 and 3 in Lisbon", "Day 1, 2 and 3 in Lisbon"),
                trace.runs().stream().map(SeriesRun::output).toList());
        assertTrue(HumanInput.prompts().isEmpty(), "the application's method is not called in a Dev UI run");
    }

    @Test
    void failedRunsAreResultsOfTheSeries() throws Exception {
        SeriesTrace trace = ended(manager, manager.startSeries(systemId("FailingFlow"), Map.of("text", "x"), 2));

        assertEquals(RunStatus.SUCCEEDED, trace.status(), "every run ended");
        for (SeriesRun run : trace.runs()) {
            assertEquals(RunStatus.FAILED, run.status());
            assertTrue(run.error().rootCauseMessage().contains("boom on x"), run.error().toString());
        }
    }

    /** Running series are never dropped: once they end, the series of the system are bounded again (TASK-99). */
    @Test
    void moreThanFiveSeriesRunningAtOnceAreBoundedWhenTheyEnd() throws Exception {
        String tripPlanner = systemId("TripPlanner");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            ids.add(manager.startSeries(tripPlanner, Map.of("wish", "a city by the sea"), 1)); // each waits for its human step
        }
        for (String seriesId : ids) {
            for (int i = 0; i < 300 && !waiting(seriesId); i++) {
                Thread.sleep(50);
            }
        }
        assertTrue(manager.listSeries(tripPlanner).size() >= 7, "running series are kept");

        ids.forEach(manager::cancelSeries);
        for (String seriesId : ids) {
            // ended, or already dropped once ended
            for (int i = 0; i < 300 && manager.series(seriesId).map(t -> t.status() == RunStatus.RUNNING).orElse(false); i++) {
                Thread.sleep(50);
            }
        }

        assertEquals(5, manager.listSeries(tripPlanner).size());
        assertTrue(manager.series(ids.get(0)).isEmpty(), "an older one left once ended");
    }

    /** At most max-concurrent-runs series run at once: no thread for a further one (TASK-99). */
    @Test
    void seriesBeyondMaxConcurrentRunsAreRefused() throws Exception {
        RunManager two = new RunManager(registry::find, new CdiRunInvoker(), PayloadTruncator.DEFAULT_MAX_SIZE, 20, 2);
        try {
            RootListeners.attach(registry.systems(), new DevUiAgentListener(two));
            String tripPlanner = systemId("TripPlanner");
            String first = two.startSeries(tripPlanner, Map.of("wish", "a city by the sea"), 1);
            two.startSeries(tripPlanner, Map.of("wish", "a city by the sea"), 1);

            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> two.startSeries(tripPlanner, Map.of("wish", "a city by the sea"), 1));
            assertTrue(refused.getMessage().contains("Too many series at once"), refused.getMessage());
            assertEquals(2, two.listSeries(tripPlanner).size(), "nothing left of the refused one");

            two.cancelSeries(first);
            for (int i = 0; i < 300 && two.series(first).orElseThrow().status() == RunStatus.RUNNING; i++) {
                Thread.sleep(50);
            }
            String third = two.startSeries(tripPlanner, Map.of("wish", "a city by the sea"), 1);
            assertNotNull(third, "accepted once one ended");
        } finally {
            two.shutdown();
        }
    }

    @Test
    void theNewestSeriesOfASystemAreKept() throws Exception {
        String researchGatherer = systemId("ResearchGatherer");
        List<String> ids = new ArrayList<>();
        List<String> firstRuns = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String seriesId = manager.startSeries(researchGatherer, Map.of("topic", "quantum computing"), 1);
            firstRuns.add(ended(manager, seriesId).runs().get(0).runId());
            ids.add(seriesId);
        }

        assertEquals(5, manager.listSeries(researchGatherer).size());
        assertTrue(manager.series(ids.get(0)).isEmpty(), "the oldest left");
        assertTrue(manager.run(firstRuns.get(0)).isEmpty(), "with its runs");
        assertTrue(manager.run(firstRuns.get(1)).isPresent());
        assertEquals(ids.get(5), manager.listSeries(researchGatherer).get(0).seriesId(), "newest first");
    }
}
