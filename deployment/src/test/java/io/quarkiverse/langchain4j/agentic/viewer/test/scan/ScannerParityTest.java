package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.declarative.RegistryAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;

/**
 * The scan resolves agents the way langchain4j-agentic does (AgenticServices, AgentUtil, AgenticParameterNameResolver):
 * inherited agent methods, the method taken among several, scope key names, registry agents, overloads, conditional
 * branches.
 */
class ScannerParityTest {

    interface BaseReviewer {
        @Agent(outputKey = "review")
        String review(@V("draft") String draft);
    }

    /** No agent method of its own: inherits the reviewer's. */
    interface StyleReviewer extends BaseReviewer {
    }

    /** Two agent methods: langchain4j takes the sequence (before @Agent), not the first by name. */
    interface TwoWays {
        @Agent(outputKey = "a")
        String alpha(@V("draft") String draft);

        @SequenceAgent(outputKey = "b", subAgents = { BaseReviewer.class })
        String beta(@V("draft") String draft);
    }

    class Story implements TypedKey<String> {
    }

    interface Keyed {
        @Agent(outputKey = "out")
        String write(@K(Story.class) String story, @LoopCounter int iteration, InvocationParameters parameters,
                @P(name = "tone", value = "the tone") String tone);
    }

    interface FromRegistry {
        @RegistryAgent("translator")
        String translate(@V("text") String text);
    }

    interface Overloaded {
        @Agent(outputKey = "one")
        String run(@V("x") String x);

        @Agent(outputKey = "two")
        String run(@V("x") String x, @V("y") int y);
    }

    /** A loop as a conditional branch, and a branch without activation condition (left out by langchain4j). */
    interface LoopRouter {
        @ConditionalAgent(outputKey = "draft", subAgents = { ScanFixtures.Polisher.class, ScanFixtures.Writer.class })
        String route(@V("draft") String draft);

        @ActivationCondition(ScanFixtures.Polisher.class)
        static boolean rough(@V("draft") String draft) {
            return true;
        }
    }

    interface Pipeline {
        @SequenceAgent(outputKey = "done", subAgents = { StyleReviewer.class, TwoWays.class, Keyed.class,
                FromRegistry.class, LoopRouter.class })
        String run(@V("draft") String draft);
    }

    private static final String T = ScannerParityTest.class.getName() + "$";
    private static Map<String, AgenticSystem> systems;

    @BeforeAll
    static void scan() throws Exception {
        Index index = Index.of(BaseReviewer.class, StyleReviewer.class, TwoWays.class, Story.class, Keyed.class,
                FromRegistry.class, Overloaded.class, LoopRouter.class, Pipeline.class, ScanFixtures.Polisher.class,
                ScanFixtures.Scorer.class, ScanFixtures.Editor.class, ScanFixtures.Writer.class, ScanFixtures.Score.class);
        systems = new TopologyBuilder().build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(index)))
                .stream().collect(Collectors.toMap(AgenticSystem::id, Function.identity()));
    }

    private static AgentNode node(String id) {
        AgentNode root = systems.get(T + "Pipeline#run").root();
        return find(root, T + "Pipeline#run" + id);
    }

    private static AgentNode find(AgentNode node, String id) {
        if (node.id().equals(id)) {
            return node;
        }
        return node.children().stream().map(child -> find(child, id)).filter(n -> n != null).findFirst().orElse(null);
    }

    @Test
    void rootsAreTheSystemsOnly() {
        // not BaseReviewer (inherited by a sub-agent), not the sub-agents; Overloaded's two methods are two systems
        assertEquals(List.of(T + "Overloaded#run(java.lang.String)", T + "Overloaded#run(java.lang.String,int)",
                T + "Pipeline#run"), systems.keySet().stream().sorted().toList());
    }

    @Test
    void anInheritedAgentMethodIsTheSubAgentsOwn() {
        AgentNode reviewer = node("/StyleReviewer");
        assertEquals(NodeKind.AI_AGENT, reviewer.kind());
        assertEquals("review", reviewer.outputKey());
        assertEquals(List.of("draft"), reviewer.inputKeys());
    }

    @Test
    void theMethodLangChain4jTakesAmongSeveral() {
        AgentNode twoWays = node("/TwoWays");
        assertEquals(NodeKind.SEQUENCE, twoWays.kind());
        assertEquals("b", twoWays.outputKey());
    }

    @Test
    void scopeKeysFollowLangChain4j() {
        assertEquals(List.of("Story", "tone"), node("/Keyed").inputKeys(),
                "typed key name, @P name; no loop counter nor invocation parameters");
    }

    @Test
    void registryAgentsHaveTheirKind() {
        AgentNode registry = node("/FromRegistry");
        assertEquals(NodeKind.REGISTRY_AGENT, registry.kind());
        assertEquals("Agent \"translator\" of the agents registry", registry.description());
    }

    @Test
    void overloadedAgentMethodsAreTwoSystemsAndRunTheirOwnMethod() throws Exception {
        AgenticSystem two = systems.get(T + "Overloaded#run(java.lang.String,int)");
        assertEquals(List.of("x", "y"), two.inputs().stream().map(SystemInput::name).toList());
        Method method = CdiRunInvoker.rootMethod(Overloaded.class, two.sourceRef().methodName());
        assertEquals(2, method.getParameterCount());
        assertEquals(1, CdiRunInvoker.rootMethod(Overloaded.class, "run(java.lang.String)").getParameterCount());
    }

    @Test
    void conditionalBranchesAreTheOnesLangChain4jKeeps() {
        AgentNode router = node("/LoopRouter");
        assertEquals(List.of(T + "Pipeline#run/LoopRouter/Polisher"), router.children().stream().map(AgentNode::id).toList(),
                "Writer has no activation condition");
        Guard guard = router.children().get(0).guard();
        assertEquals("goodEnough", guard.label());
        assertEquals(3, guard.maxIterations());
        assertEquals("rough", guard.activation());
        assertTrue(systems.values().stream().noneMatch(s -> s.id().contains("Writer")));
    }
}
