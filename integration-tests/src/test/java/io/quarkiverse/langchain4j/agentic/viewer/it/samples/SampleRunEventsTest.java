package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Runs of the sample systems report the agentic callbacks as events, through the Dev UI listener attached to the root
 * agents (as at dev-mode startup).
 */
@QuarkusTest
class SampleRunEventsTest {

    private static final Map<String, Map<String, Object>> INPUTS = Map.of(
            "StoryWriter", Map.of("bio", "Ada, a curious engineer"),
            "ResearchGatherer", Map.of("topic", "quantum computing"),
            "PhraseTranslator", Map.of("phrases", List.of("hello", "thank you", "good night")),
            "StoryCreator", Map.of("topic", "a lighthouse"),
            "ExpertRouter", Map.of("request", "I hurt my leg while running"),
            "BankSupervisor", Map.of("request", BankSupervisor.Script.request()),
            "DraftReviewer", Map.of("topic", "solar panels"),
            "TripPlanner", Map.of("wish", "a city by the sea"),
            "ResilientStoryWriter", Map.of("topic", "pirates"));

    @Inject
    ScriptedChatModel model;

    @Inject
    BankSupervisor.BankTool bankTool;

    private static List<AgenticSystem> systems;
    private static RunManager runManager;

    @BeforeAll
    static void observeRoots() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        systems = new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples))));
        runManager = manager();
    }

    private static RunManager manager() {
        RunManager manager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(),
                new CdiRunInvoker());
        assertEquals(systems.size(), RootListeners.attach(systems, new DevUiAgentListener(manager)),
                "listener attached to every root");
        return manager;
    }

    @BeforeEach
    void reset() {
        model.clearRequests();
        bankTool.reset();
        HumanInput.reset();
    }

    private static AgenticSystem system(String name) {
        return systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private static Run run(RunManager manager, String name) throws Exception {
        Run run = manager.run(manager.startRun(system(name).id(), INPUTS.get(name))).orElseThrow();
        run.completion().get(60, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> name + ": " + run.events());
        return run;
    }

    private static Set<String> nodeIds(AgentNode node) {
        Set<String> ids = new java.util.HashSet<>();
        ids.add(node.id());
        node.children().forEach(child -> ids.addAll(nodeIds(child)));
        return ids;
    }

    @Test
    void everySampleReportsScopeAndAgentEventsOnItsTopology() throws Exception {
        for (String name : INPUTS.keySet()) {
            reset();
            AgenticSystem system = system(name);
            List<ExecutionEvent> events = run(runManager, name).events();
            List<String> types = events.stream().map(ExecutionEvent::getType).toList();

            assertEquals("RunStarted", types.get(0), name);
            assertEquals("RunFinished", types.get(types.size() - 1), name);
            assertTrue(types.contains("ScopeCreated") && types.contains("ScopeDestroyed"), name + ": " + types);

            List<AgentStarted> started = events.stream().filter(AgentStarted.class::isInstance)
                    .map(AgentStarted.class::cast).toList();
            Set<String> finished = events.stream().filter(AgentFinished.class::isInstance)
                    .map(e -> ((AgentFinished) e).invocationId()).collect(Collectors.toSet());
            assertEquals(system.id(), started.get(0).nodeId(), name + ": the root runs first");
            assertTrue(nodeIds(system.root()).containsAll(started.stream().map(AgentStarted::nodeId).toList()),
                    name + ": node ids from the topology " + started.stream().map(AgentStarted::nodeId).toList());
            assertEquals(started.stream().map(AgentStarted::invocationId).collect(Collectors.toSet()), finished,
                    name + ": every invocation finished");
            assertTrue(started.stream().skip(1).allMatch(e -> e.parentInvocationId() != null), name + ": parents");
            assertTrue(events.stream().allMatch(e -> e.runId().equals(events.get(0).runId())), name);
        }
    }

    @Test
    void loopAndToolEvents() throws Exception {
        List<ExecutionEvent> story = run(runManager, "StoryCreator").events();
        String root = system("StoryCreator").id();
        assertEquals(List.of(root, root + "/Drafter", root + "/StyleReviewLoop",
                root + "/StyleReviewLoop/StyleScorer", root + "/StyleReviewLoop/StyleEditor",
                root + "/StyleReviewLoop/StyleScorer", root + "/StyleReviewLoop/StyleEditor",
                root + "/StyleReviewLoop/StyleScorer", root + "/StyleReviewLoop/StyleEditor"),
                story.stream().filter(AgentStarted.class::isInstance).map(e -> ((AgentStarted) e).nodeId()).toList());

        reset();
        List<ExecutionEvent> bank = run(runManager, "BankSupervisor").events();
        assertEquals(List.of("exchange", "withdraw", "credit"), bank.stream().filter(ToolStarted.class::isInstance)
                .map(e -> ((ToolStarted) e).toolName()).toList());
        assertTrue(bank.stream().filter(ToolStarted.class::isInstance)
                .allMatch(e -> ((ToolStarted) e).invocationId() != null), "tool calls belong to the calling agent");
        assertEquals(bank.stream().filter(e -> e.getType().equals("ToolStarted")).count(),
                bank.stream().filter(e -> e.getType().equals("ToolFinished")).count());
    }

    @Test
    void slowSubscriberDoesNotSlowTheRun() throws Exception {
        RunManager slowManager = manager();
        List<ExecutionEvent> received = new CopyOnWriteArrayList<>();
        long sleepMillis = 150;
        slowManager.addListener(event -> {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            received.add(event);
        });

        Run run = run(slowManager, "StoryCreator");

        List<ExecutionEvent> events = run.events();
        long runDuration = ((RunFinished) events.get(events.size() - 1)).durationMillis();
        assertTrue(events.size() >= 20, events::toString);
        assertTrue(runDuration < events.size() * sleepMillis / 2,
                "run took " + runDuration + " ms with " + events.size() + " events delivered " + sleepMillis + " ms apart");
        assertEquals(new ArrayList<>(events), received, "every event reached the slow subscriber, in order");
    }
}
