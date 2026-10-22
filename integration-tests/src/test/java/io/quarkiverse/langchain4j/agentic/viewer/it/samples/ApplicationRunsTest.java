package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BlurbedStory;
import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BuilderStoryCreator;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderTopology;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AwaitingHumanInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeCreated;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.ApplicationRuns;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunOrigin;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunSummary;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the application starts itself (TASK-95): calls of the root agents outside a Dev UI run are recorded as
 * application runs, observed only, bounded apart from the Dev UI runs.
 */
@QuarkusTest
class ApplicationRunsTest {

    private static final String BUILDER = "applicationRunsBuilderStoryCreator";
    private static final int APPLICATION_HISTORY = 3;

    @Inject
    StoryWriter storyWriter;

    @Inject
    TripPlanner tripPlanner;

    @Inject
    FailingFlow failingFlow;

    @Inject
    DraftReviewer draftReviewer;

    @Inject
    BlurbedStory blurbedStory;

    @Inject
    @Named(BuilderStoryCreator.NAME)
    UntypedAgent builderStoryCreator;

    private static AgenticSystemRegistry registry;
    /** Records application runs. */
    private static RunManager manager;
    /** Records at most one open application run per system. */
    private static RunManager capped;
    /** Does not record them (a manager built in code, application runs not enabled). */
    private static RunManager off;
    private static AgenticViewerJsonRpcService service;

    /** Once: a listener cannot be removed from the application-scoped roots. */
    @BeforeEach
    void observe() throws Exception {
        HumanInput.reset();
        if (manager != null) {
            manager.recordApplicationRuns(true);
            return;
        }
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        List<AgenticSystem> systems = new ArrayList<>(scan(Index.of(samples)));
        systems.addAll(scan(Index.of(FailingFlow.class, FailingFlow.Exploder.class)));
        systems.addAll(scan(Index.of(BlurbedStory.class, BlurbedStory.StoryStep.class, BlurbedStory.BlurbWriter.class)));
        Object builderRoot = ClientProxy.unwrap(builderStoryCreator);
        AgenticSystem builder = BuilderTopology.system(BUILDER, BUILDER, new SourceRef(BUILDER, BUILDER, null),
                (AgentInstance) builderRoot);
        registry = new AgenticSystemRegistry();
        systems.forEach(registry::register);
        registry.register(builder);
        manager = new RunManager(registry::find, new CdiRunInvoker()).enableApplicationRuns(APPLICATION_HISTORY, 16);
        capped = new RunManager(registry::find, new CdiRunInvoker()).enableApplicationRuns(20, 1);
        off = new RunManager(registry::find, new CdiRunInvoker());
        for (RunManager each : List.of(manager, capped, off)) {
            DevUiAgentListener listener = new DevUiAgentListener(each);
            RootListeners.attach(systems, listener);
            RootListeners.attach(BUILDER, builderRoot, listener);
        }
        service = new AgenticViewerJsonRpcService(registry, manager);
    }

    @AfterEach
    void resetHumanInput() {
        HumanInput.reset();
    }

    @AfterAll
    static void shutDown() {
        for (RunManager each : new RunManager[] { manager, capped, off }) {
            if (each != null) {
                each.shutdown();
            }
        }
    }

    private static List<AgenticSystem> scan(Index index) {
        return new TopologyBuilder().build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(index)));
    }

    private static String systemId(String name) {
        return registry.systems().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow().id();
    }

    private static List<RunSummary> applicationRuns(RunManager runManager, String systemName) {
        return runManager.listRuns(systemId(systemName)).stream().filter(s -> s.origin() == RunOrigin.APPLICATION).toList();
    }

    /** The newest application run of a system, once ended. */
    private static Run lastApplicationRun(RunManager runManager, String systemName) throws Exception {
        List<RunSummary> runs = applicationRuns(runManager, systemName);
        assertFalse(runs.isEmpty(), "no application run of " + systemName);
        Run run = runManager.run(runs.getFirst().runId()).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        return run;
    }

    private static <T extends ExecutionEvent> List<T> events(Run run, Class<T> type) {
        return run.events().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void anApplicationCallIsRecordedWithItsEvents() throws Exception {
        String story = storyWriter.writeStory("Ada, a curious engineer");

        Run run = lastApplicationRun(manager, "StoryWriter");
        assertEquals(RunOrigin.APPLICATION, run.origin());
        assertEquals(RunStatus.SUCCEEDED, run.status());
        assertEquals(story, run.output());
        assertEquals(Map.of("bio", "Ada, a curious engineer"), run.inputs());
        List<ExecutionEvent> events = run.events();
        assertInstanceOf(RunStarted.class, events.get(0), events.toString());
        assertInstanceOf(ScopeCreated.class, events.get(1), events.toString());
        assertTrue(events.getLast() instanceof RunFinished finished && story.equals(finished.output()));
        List<String> nodes = events(run, AgentStarted.class).stream().map(AgentStarted::nodeId).toList();
        String root = systemId("StoryWriter");
        assertEquals(List.of(root, root + "/IntroWriter", root + "/AdventureWriter"), nodes);
    }

    @Test
    void aHumanStepOfAnApplicationRunCallsTheApplicationMethod() throws Exception {
        assertEquals("Day 1, 2 and 3 in Lisbon", tripPlanner.plan("a city by the sea"));

        assertEquals(List.of("Do you want to travel to Lisbon?"), HumanInput.prompts());
        Run run = lastApplicationRun(manager, "TripPlanner");
        assertEquals(RunStatus.SUCCEEDED, run.status());
        assertTrue(events(run, AwaitingHumanInput.class).isEmpty(), "not intercepted");
        assertTrue(events(run, AgentStarted.class).stream().anyMatch(e -> e.nodeId().endsWith("/TravelerApproval")));
    }

    @Test
    void thePlannerDecisionsOfAnApplicationRunAreRecorded() throws Exception {
        assertEquals("A polished draft about solar panels", draftReviewer.write("solar panels"));

        Run run = lastApplicationRun(manager, "DraftReviewer");
        List<PlannerDecision> decisions = events(run, PlannerDecision.class);
        assertEquals(List.of(false, false, false, false, true), decisions.stream().map(PlannerDecision::done).toList());
    }

    @Test
    void aFailingApplicationCallFailsAsWithoutTheViewer() throws Exception {
        manager.recordApplicationRuns(false);
        RuntimeException unrecorded = assertThrows(RuntimeException.class, () -> failingFlow.run("x"));
        manager.recordApplicationRuns(true);
        RuntimeException recorded = assertThrows(RuntimeException.class, () -> failingFlow.run("x"));

        assertEquals(unrecorded.getClass(), recorded.getClass());
        assertEquals(unrecorded.getMessage(), recorded.getMessage());
        Run run = lastApplicationRun(manager, "FailingFlow");
        assertEquals(RunStatus.FAILED, run.status());
        RunFailed failed = (RunFailed) run.events().getLast();
        assertEquals(recorded.getClass().getName(), failed.error().exceptionClass(), "what the application got");
        assertTrue(failed.error().rootCauseMessage().contains("boom on x"), failed.error().rootCauseMessage());
    }

    @Test
    void pausedOrOffNothingIsRecorded() {
        int before = applicationRuns(manager, "StoryWriter").size();
        ApplicationRuns paused = service.setRecordApplicationRuns(systemId("StoryWriter"), false);
        assertTrue(paused.enabled());
        assertFalse(paused.recording());

        storyWriter.writeStory("Grace");

        assertEquals(before, applicationRuns(manager, "StoryWriter").size());
        assertTrue(off.listRuns(systemId("StoryWriter")).isEmpty());
        assertFalse(off.applicationRuns(systemId("StoryWriter")).enabled());
        assertTrue(service.setRecordApplicationRuns(systemId("StoryWriter"), true).recording());
    }

    @Test
    void beyondTheCapARunIsSkippedAndCounted() throws Exception {
        String tripPlannerId = systemId("TripPlanner");
        long skippedBefore = capped.applicationRuns(tripPlannerId).skipped();
        CountDownLatch release = new CountDownLatch(1);
        HumanInput.answerWith(prompt -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return HumanInput.DEFAULT_ANSWER;
        });
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> tripPlanner.plan("a city by the sea"));
        RunSummary open = awaitOpen(capped, "TripPlanner");
        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> tripPlanner.plan("a city by the sea"));
        awaitPrompts(2); // the second call reached its human step: its root call is past
        assertFalse(capped.cancel(open.runId()), "an application run cannot be cancelled");
        release.countDown();

        assertEquals("Day 1, 2 and 3 in Lisbon", first.get(30, TimeUnit.SECONDS));
        assertEquals("Day 1, 2 and 3 in Lisbon", second.get(30, TimeUnit.SECONDS));
        assertEquals(skippedBefore + 1, capped.applicationRuns(tripPlannerId).skipped());
        assertEquals(List.of(open.runId()), applicationRuns(capped, "TripPlanner").stream()
                .filter(s -> s.startedAt() >= open.startedAt()).map(RunSummary::runId).toList(), "only the first one recorded");
        assertEquals(RunStatus.SUCCEEDED, capped.run(open.runId()).orElseThrow().completion().get(30, TimeUnit.SECONDS)
                .status());
    }

    private static RunSummary awaitOpen(RunManager runManager, String systemName) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            for (RunSummary summary : applicationRuns(runManager, systemName)) {
                if (summary.status() == RunStatus.RUNNING) {
                    return summary;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no open application run of " + systemName);
    }

    private static void awaitPrompts(int count) throws InterruptedException {
        for (int i = 0; i < 300 && HumanInput.prompts().size() < count; i++) {
            Thread.sleep(50);
        }
        assertEquals(count, HumanInput.prompts().size());
    }

    @Test
    void applicationRunsNeverPushOutTheDevUiRuns() throws Exception {
        String storyWriterId = systemId("StoryWriter");
        String devUiRun = manager.startRun(storyWriterId, Map.of("bio", "Ada"));
        manager.run(devUiRun).orElseThrow().completion().get(30, TimeUnit.SECONDS);

        for (int i = 0; i < APPLICATION_HISTORY + 3; i++) {
            storyWriter.writeStory("Grace " + i);
        }
        lastApplicationRun(manager, "StoryWriter"); // ended: the history is bounded again

        List<RunSummary> runs = manager.listRuns(storyWriterId);
        assertTrue(runs.stream().anyMatch(s -> s.runId().equals(devUiRun) && s.origin() == RunOrigin.DEV_UI), runs.toString());
        assertEquals(APPLICATION_HISTORY, applicationRuns(manager, "StoryWriter").size());
        assertEquals(Map.of("bio", "Grace " + (APPLICATION_HISTORY + 2)), runs.getFirst().inputs(), "newest first");
        assertFalse(service.cancelRun(runs.getFirst().runId()));
    }

    @Test
    void aSystemCalledByAnAgentOfAnApplicationRunIsPartOfIt() throws Exception {
        int builderRunsBefore = manager.listRuns(BUILDER).size();

        assertEquals("A tale you will not forget.", blurbedStory.create("a lighthouse"));

        Run run = lastApplicationRun(manager, "BlurbedStory");
        List<String> agents = events(run, AgentStarted.class).stream().map(AgentStarted::agentName).toList();
        assertTrue(agents.containsAll(List.of("create", "story", "polish", "blurb")), agents.toString());
        assertEquals(builderRunsBefore, manager.listRuns(BUILDER).size(), "no application run of its own");
    }

    @Test
    void concurrentApplicationCallsAreSeparateRuns() throws Exception {
        String storyWriterId = systemId("StoryWriter");
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<String>> calls = new ArrayList<>();
        for (String bio : List.of("Ada", "Grace")) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return storyWriter.writeStory(bio);
            }));
        }
        start.countDown();
        for (CompletableFuture<String> call : calls) {
            call.get(30, TimeUnit.SECONDS);
        }

        List<RunSummary> runs = applicationRuns(manager, "StoryWriter").subList(0, 2);
        assertEquals(List.of("Ada", "Grace"), runs.stream().map(s -> (String) s.inputs().get("bio")).sorted().toList());
        for (RunSummary summary : runs) {
            Run run = manager.run(summary.runId()).orElseThrow();
            run.completion().get(30, TimeUnit.SECONDS);
            assertEquals(3, events(run, AgentStarted.class).size(), run.events().toString());
            assertTrue(((String) run.output()).contains((String) summary.inputs().get("bio")));
            assertEquals(storyWriterId, events(run, AgentStarted.class).getFirst().nodeId());
        }
    }

    @Test
    void theDevUiIsToldOfEachNewApplicationRun() {
        String storyWriterId = systemId("StoryWriter");
        AssertSubscriber<RunSummary> subscriber = service.streamApplicationRuns(storyWriterId)
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        storyWriter.writeStory("Ada");
        storyWriter.writeStory("Grace");

        subscriber.awaitItems(2, Duration.ofSeconds(10));
        subscriber.cancel();
        assertEquals(List.of("Ada", "Grace"), subscriber.getItems().stream().map(s -> s.inputs().get("bio")).toList());
        assertTrue(subscriber.getItems().stream().allMatch(s -> s.origin() == RunOrigin.APPLICATION));
        assertTrue(service.getApplicationRuns(storyWriterId).recording());
    }
}
