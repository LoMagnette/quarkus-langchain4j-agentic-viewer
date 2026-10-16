package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeStateChanged;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolFinished;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;

/**
 * The exact event sequence of a run per pattern kind, through the JSON-RPC service (startRun, then streamRun), on the
 * scripted fake model: guards the listener, the correlation and the broadcaster against regressions and upstream
 * changes. The websocket transport itself is covered by the deployment dev-mode tests.
 */
@QuarkusTest
class JsonRpcEventSequenceTest {

    @Inject
    ScriptedChatModel model;

    @Inject
    BankSupervisor.BankTool bankTool;

    private static AgenticViewerJsonRpcService service;
    private static Map<String, String> idsByName;

    @BeforeAll
    static void service() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        List<AgenticSystem> systems = new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples))));
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        systems.forEach(registry::register);
        RunManager runManager = new RunManager(registry::find, new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager));
        service = new AgenticViewerJsonRpcService(registry, runManager);
        idsByName = new LinkedHashMap<>();
        systems.forEach(system -> idsByName.put(system.name(), system.id()));
    }

    @BeforeEach
    void reset() {
        model.clearRequests();
        bankTool.reset();
        HumanInput.reset();
    }

    /** startRun then streamRun, as the Dev UI does; the stream replays what was emitted before subscribing. */
    private static List<ExecutionEvent> run(String system, Map<String, Object> inputs) {
        String runId = service.startRun(idsByName.get(system), inputs);
        AssertSubscriber<ExecutionEvent> subscriber = service.streamRun(runId)
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        subscriber.awaitCompletion(Duration.ofSeconds(30));
        return subscriber.getItems();
    }

    /** One compact line per event: type, node (relative to the root), iteration, item, key or tool. */
    private static String line(String root, ExecutionEvent event) {
        String node = switch (event) {
            case AgentStarted e -> e.nodeId();
            case AgentFinished e -> e.nodeId();
            case ScopeStateChanged e -> e.nodeId();
            case PlannerDecision e -> e.nodeId();
            default -> null;
        };
        String where = node == null ? "" : " " + (node.equals(root) ? "/" : node.substring(root.length()));
        String detail = switch (event) {
            case AgentStarted e -> (e.iteration() != null ? " #" + e.iteration() : "") + (e.itemIndex() != null ? " item " + e.itemIndex() : "");
            case ScopeStateChanged e -> " " + e.key();
            case ToolStarted e -> " " + e.toolName();
            case ToolFinished e -> " " + e.toolName();
            case PlannerDecision e -> " " + e.extras().get("action")
                    + e.selectedNodeIds().stream().map(id -> " " + id.substring(id.lastIndexOf('/') + 1)).collect(Collectors.joining());
            default -> "";
        };
        return event.getType() + where + detail;
    }

    private static List<String> lines(String system, List<ExecutionEvent> events) {
        String root = idsByName.get(system);
        return events.stream().map(e -> line(root, e)).toList();
    }

    @Test
    void sequence() {
        assertEquals(List.of(
                "RunStarted",
                "ScopeCreated",
                "AgentStarted /",
                "AgentStarted /IntroWriter",
                "AgentFinished /IntroWriter",
                "ScopeStateChanged /IntroWriter intro",
                "AgentStarted /AdventureWriter",
                "AgentFinished /AdventureWriter",
                "ScopeStateChanged /AdventureWriter adventure",
                "AgentFinished /",
                "ScopeStateChanged / story",
                "ScopeDestroyed",
                "RunFinished"), lines("StoryWriter", run("StoryWriter", Map.of("bio", "Ada"))));
    }

    @Test
    void loopInSequence() {
        List<String> expected = new ArrayList<>(List.of(
                "RunStarted", "ScopeCreated", "AgentStarted /",
                "AgentStarted /Drafter", "AgentFinished /Drafter", "ScopeStateChanged /Drafter story",
                "AgentStarted /StyleReviewLoop"));
        for (int i = 0; i < 3; i++) {
            expected.addAll(List.of(
                    "AgentStarted /StyleReviewLoop/StyleScorer #" + i, "AgentFinished /StyleReviewLoop/StyleScorer",
                    "ScopeStateChanged /StyleReviewLoop/StyleScorer score",
                    "AgentStarted /StyleReviewLoop/StyleEditor #" + i, "AgentFinished /StyleReviewLoop/StyleEditor",
                    "ScopeStateChanged /StyleReviewLoop/StyleEditor story"));
        }
        expected.addAll(List.of(
                "AgentFinished /StyleReviewLoop", "ScopeStateChanged /StyleReviewLoop story",
                "AgentFinished /", "ScopeStateChanged / story", "ScopeDestroyed", "RunFinished"));
        assertEquals(expected, lines("StoryCreator", run("StoryCreator", Map.of("topic", "a lighthouse"))));
    }

    @Test
    void conditional() {
        assertEquals(List.of(
                "RunStarted", "ScopeCreated", "AgentStarted /",
                "AgentStarted /CategoryRouter", "AgentFinished /CategoryRouter", "ScopeStateChanged /CategoryRouter category",
                "AgentStarted /Experts",
                "AgentStarted /Experts/MedicalExpert", "AgentFinished /Experts/MedicalExpert",
                "ScopeStateChanged /Experts/MedicalExpert response",
                "AgentFinished /Experts", "ScopeStateChanged /Experts response",
                "AgentFinished /", "ScopeStateChanged / response", "ScopeDestroyed", "RunFinished"),
                lines("ExpertRouter", run("ExpertRouter", Map.of("request", "I hurt my leg while running"))));
    }

    @Test
    void supervisorWithTools() {
        List<String> agents = List.of("ExchangeAgent", "WithdrawAgent", "CreditAgent");
        List<String> tools = List.of("exchange", "withdraw", "credit");
        List<String> keys = List.of("amountInUSD", "withdrawn", "credited");
        List<String> expected = new ArrayList<>(List.of("RunStarted", "ScopeCreated", "AgentStarted /"));
        for (int i = 0; i < 3; i++) {
            expected.addAll(List.of("PlannerDecision / call " + agents.get(i),
                    "AgentStarted /" + agents.get(i), "ToolStarted " + tools.get(i),
                    "ToolFinished " + tools.get(i), "AgentFinished /" + agents.get(i),
                    "ScopeStateChanged /" + agents.get(i) + " " + keys.get(i)));
        }
        expected.addAll(List.of("PlannerDecision / doneWithResult", "AgentFinished /", "ScopeStateChanged / result",
                "ScopeDestroyed", "RunFinished"));
        assertEquals(expected, lines("BankSupervisor", run("BankSupervisor", Map.of("request", BankSupervisor.Script.request()))));
    }

    /** Branches interleave: the sequence outside the branches and the sequence of each branch are exact. */
    private static void assertBranches(List<String> all, List<String> outside, Map<String, List<String>> branches) {
        List<String> rest = new ArrayList<>(all);
        branches.forEach((branch, sequence) -> {
            List<String> actual = all.stream().filter(line -> line.contains(" " + branch)).toList();
            assertEquals(sequence, actual, branch);
            rest.removeAll(actual);
        });
        assertEquals(outside, rest);
    }

    @Test
    void parallel() {
        List<String> lines = lines("ResearchGatherer", run("ResearchGatherer", Map.of("topic", "quantum computing")));
        Map<String, List<String>> branches = new LinkedHashMap<>();
        Map.of("WebSearcher", "webResults", "DatabaseSearcher", "dbResults", "ArchiveSearcher", "archiveResults")
                .forEach((agent, key) -> branches.put("/" + agent, List.of("AgentStarted /" + agent,
                        "AgentFinished /" + agent, "ScopeStateChanged /" + agent + " " + key)));
        assertBranches(lines, List.of("RunStarted", "ScopeCreated", "AgentStarted /", "AgentFinished /",
                "ScopeStateChanged / research", "ScopeDestroyed", "RunFinished"), branches);
    }

    /**
     * The items run concurrently, so their events interleave: each item writes its own {@code translation_<index>} key,
     * in whatever order the items complete.
     */
    @Test
    void parallelMapper() {
        List<String> lines = lines("PhraseTranslator",
                run("PhraseTranslator", Map.of("phrases", List.of("hello", "thank you", "good night"))));
        Map<String, List<String>> items = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            items.put("/Translator item " + i, List.of("AgentStarted /Translator item " + i));
        }
        List<String> rest = new ArrayList<>(lines);
        items.values().forEach(rest::removeAll);
        assertEquals(List.of("RunStarted", "ScopeCreated", "AgentStarted /"), rest.subList(0, 3));
        assertEquals(List.of("AgentFinished /", "ScopeStateChanged / translations", "ScopeDestroyed", "RunFinished"),
                rest.subList(rest.size() - 4, rest.size()));
        assertEquals(List.of("AgentFinished /Translator", "AgentFinished /Translator", "AgentFinished /Translator",
                "ScopeStateChanged /Translator translation_0", "ScopeStateChanged /Translator translation_1",
                "ScopeStateChanged /Translator translation_2"),
                rest.subList(3, rest.size() - 4).stream().sorted().toList());
        for (int i = 0; i < 3; i++) {
            assertTrue(lines.indexOf("AgentStarted /Translator item " + i)
                    < lines.indexOf("ScopeStateChanged /Translator translation_" + i), "item " + i);
        }
        assertBranches(lines, rest, items);
    }
}
