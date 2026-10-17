package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.it.fake.ScriptedChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ProductLaunch.Audience;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ProductLaunch.Brief;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The larger sample: several patterns (parallel, parallel mapper, loop, conditional with a nested sequence) and
 * inputs of every kind the run form handles (text, enum, integer, list, record).
 */
@QuarkusTest
class ProductLaunchTest {

    private static final String PREMIUM_PLAN = "Launch plan of Photon, Campaign (v4)\n"
            + "Pitches: pitch for Belgium | pitch for Japan\n"
            + "Channels: paid media for 20000 + an event for Photon";

    @Inject
    ScriptedChatModel model;

    @Inject
    ProductLaunch productLaunch;

    private static AgenticSystem system;
    private static RunManager runManager;
    private static AgenticViewerJsonRpcService service;

    @BeforeAll
    static void observe() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        system = new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples)))).stream()
                .filter(s -> s.name().equals("ProductLaunch")).findFirst().orElseThrow();
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        registry.register(system);
        runManager = new RunManager(registry::find, new CdiRunInvoker());
        RootListeners.attach(List.of(system), new DevUiAgentListener(runManager));
        service = new AgenticViewerJsonRpcService(registry, runManager);
    }

    @BeforeEach
    void reset() {
        model.clearRequests();
    }

    private long requestsContaining(String text) {
        return model.requests().stream().filter(request -> ScriptedChatModel.lastUserMessage(request).contains(text))
                .count();
    }

    private static List<AgentNode> nodes(AgentNode node) {
        List<AgentNode> nodes = new ArrayList<>(List.of(node));
        node.children().forEach(child -> nodes.addAll(nodes(child)));
        return nodes;
    }

    @Test
    void premiumBudget() {
        assertEquals(PREMIUM_PLAN, productLaunch.plan("Photon", Audience.DEVELOPERS, 20_000, List.of("Belgium", "Japan"),
                new Brief("playful", 50)));
        assertEquals(2, requestsContaining("Write a launch pitch for the market:"), "one pitch per market");
        assertEquals(3, requestsContaining("Score this campaign"), "loop iterations");
        assertEquals(0, requestsContaining("Plan the social media launch"), "premium branch only");
    }

    @Test
    void smallBudget() {
        String plan = productLaunch.plan("Photon", Audience.CONSUMERS, 5_000, List.of("Brazil"), new Brief("calm", 20));
        assertTrue(plan.endsWith("Channels: social media for Photon"), plan);
        assertEquals(0, requestsContaining("Plan the paid media"), "digital branch only");
    }

    /** The graph: 17 nodes, every pattern. */
    @Test
    void topology() {
        List<AgentNode> nodes = nodes(system.root());
        assertEquals(17, nodes.size());
        assertTrue(nodes.stream().map(AgentNode::kind).toList().containsAll(List.of(NodeKind.SEQUENCE, NodeKind.PARALLEL,
                NodeKind.PARALLEL_MAPPER, NodeKind.LOOP, NodeKind.CONDITIONAL, NodeKind.AI_AGENT)));
    }

    /** One field per input, each typed: text, enum, integer, list, record. */
    @Test
    void runFormSchema() {
        assertEquals(Map.of(
                "type", "object",
                "properties", Map.of(
                        "product", Map.of("type", "string"),
                        "audience", Map.of("type", "string", "enum", List.of("CONSUMERS", "DEVELOPERS", "ENTERPRISES")),
                        "budget", Map.of("type", "integer"),
                        "markets", Map.of("type", "array", "items", Map.of("type", "string")),
                        "brief", Map.of("type", "object", "properties", Map.of("tone", Map.of("type", "string"),
                                "maxWords", Map.of("type", "integer")), "required", List.of("tone", "maxWords"))),
                "required", List.of("product", "audience", "budget", "markets", "brief")),
                service.getInputSchema(system.id()));
    }

    /** A Dev UI run with the form's JSON values: every input converted, every agent event on a node of the graph. */
    @Test
    void runFromTheForm() throws Exception {
        String runId = service.startRun(system.id(), Map.of("product", "Photon", "audience", "DEVELOPERS",
                "budget", 20000, "markets", List.of("Belgium", "Japan"), "brief", Map.of("tone", "playful", "maxWords", 50)));
        Run run = runManager.run(runId).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);

        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
        assertEquals(PREMIUM_PLAN, run.output());
        List<String> nodeIds = nodes(system.root()).stream().map(AgentNode::id).toList();
        List<AgentStarted> started = run.events().stream().filter(AgentStarted.class::isInstance)
                .map(AgentStarted.class::cast).toList();
        assertTrue(started.stream().allMatch(e -> nodeIds.contains(e.nodeId())), () -> started.toString());
        // root, research + 3, mapper + 2 items, drafter, loop + 3 x 2, conditional, premium + 2, summarizer
        assertEquals(21, started.size(), () -> started.stream().map(AgentStarted::nodeId).toList().toString());
    }
}
