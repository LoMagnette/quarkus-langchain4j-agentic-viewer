package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ScopeCreated;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ToolStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The timeline (invocations nested by parent invocation, tool calls under their agent, as the Dev UI builds it from the
 * events) matches what langchain4j's AgentMonitor records for the same run (decision-4: AgentMonitor is the test
 * oracle). Siblings are compared in a normalized order: parallel callbacks may reach the two listeners in different
 * orders.
 */
@QuarkusTest
class TimelineMatchesAgentMonitorTest {

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
    private static AgentMonitor monitor;

    @BeforeAll
    static void observe() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        systems = new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples))));
        runManager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(),
                new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager));
        HumanInputAnswers.answerInTheDevUi(runManager);
        monitor = new AgentMonitor();
        monitor.setMaxRetainedSessions(1000);
        RootListeners.attach(systems, monitor);
    }

    /** name, then children and tool calls in brackets, siblings sorted */
    private static String canonical(String name, List<String> children) {
        List<String> sorted = new ArrayList<>(children);
        sorted.sort(null);
        return sorted.isEmpty() ? name : name + sorted;
    }

    private static String fromMonitor(AgentInvocation invocation) {
        List<String> children = new ArrayList<>();
        invocation.nestedInvocations().forEach(child -> children.add(fromMonitor(child)));
        invocation.toolExecutions().forEach(tool -> children.add("tool:" + tool.request().name()));
        return canonical(invocation.agent().name(), children);
    }

    /** The Dev UI timeline nesting: AgentStarted by parent invocation, tool calls by calling invocation. */
    private static String fromEvents(List<ExecutionEvent> events) {
        Map<String, AgentStarted> invocations = new LinkedHashMap<>();
        Map<String, List<String>> children = new LinkedHashMap<>();
        String root = null;
        for (ExecutionEvent event : events) {
            if (event instanceof AgentStarted started) {
                invocations.put(started.invocationId(), started);
                children.put(started.invocationId(), new ArrayList<>());
                if (started.parentInvocationId() == null) {
                    root = started.invocationId();
                }
            }
        }
        for (ExecutionEvent event : events) {
            if (event instanceof AgentStarted started && started.parentInvocationId() != null) {
                children.get(started.parentInvocationId()).add(started.invocationId());
            }
        }
        Map<String, List<String>> tools = events.stream().filter(ToolStarted.class::isInstance).map(ToolStarted.class::cast)
                .collect(Collectors.groupingBy(ToolStarted::invocationId, Collectors.mapping(t -> "tool:" + t.toolName(),
                        Collectors.toList())));
        return render(root, invocations, children, tools);
    }

    private static String render(String id, Map<String, AgentStarted> invocations, Map<String, List<String>> children,
            Map<String, List<String>> tools) {
        List<String> nested = new ArrayList<>();
        children.get(id).forEach(child -> nested.add(render(child, invocations, children, tools)));
        nested.addAll(tools.getOrDefault(id, List.of()));
        return canonical(invocations.get(id).agentName(), nested);
    }

    @Test
    void everySampleTimelineMatchesAgentMonitor() throws Exception {
        for (String name : INPUTS.keySet()) {
            model.clearRequests();
            bankTool.reset();
            HumanInput.reset();
            AgenticSystem system = systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
            Run run = runManager.run(runManager.startRun(system.id(), INPUTS.get(name))).orElseThrow();
            run.completion().get(60, TimeUnit.SECONDS);
            assertEquals(RunStatus.SUCCEEDED, run.status(), name);

            String memoryId = run.events().stream().filter(ScopeCreated.class::isInstance)
                    .map(e -> ((ScopeCreated) e).memoryId()).findFirst().orElseThrow();
            List<MonitoredExecution> executions = monitor.successfulExecutions().stream()
                    .filter(e -> String.valueOf(e.memoryId()).equals(memoryId)).toList();
            assertEquals(1, executions.size(), name + ": one monitored execution for the run's memory id");

            String expected = fromMonitor(executions.get(0).topLevelInvocations());
            String actual = fromEvents(run.events());
            assertEquals(expected, actual, name);
            assertTrue(actual.length() > 0);
        }
    }
}
