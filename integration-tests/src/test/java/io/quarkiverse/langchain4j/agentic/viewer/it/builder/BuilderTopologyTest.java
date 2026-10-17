package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.BankSupervisor;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.DraftReviewer;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ExpertRouter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.PhraseTranslator;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ResearchGatherer;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryCreator;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryWriter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderTopology;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * T5.2: the topology of builder systems, read from their live agent tree, in the model of the declarative ones.
 */
@QuarkusTest
class BuilderTopologyTest {

    private static Map<String, AgenticSystem> declarative;

    @Inject
    @Named(BuilderStoryCreator.NAME)
    UntypedAgent builderStoryCreator;

    @Inject
    StoryCreator storyCreator;

    @Inject
    ChatModel chatModel;










    @BeforeAll
    static void declarativeSystems() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        declarative = new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples)))).stream()
                .collect(Collectors.toMap(AgenticSystem::name, Function.identity()));
    }

    private static AgentInstance instance(Object agent) {
        return (AgentInstance) (agent instanceof ClientProxy ? ClientProxy.unwrap(agent) : agent);
    }

    /** A new agent: the app's agent beans belong to its declarative systems (an agent has one parent). */
    private Object fresh(Class<?> agentInterface) {
        return AgenticServices.agentBuilder(agentInterface).chatModel(chatModel).build();
    }

    private static AgenticSystem system(Object root) {
        return BuilderTopology.system("test#system", "TestSystem", new SourceRef("Test", "system", null), instance(root));
    }

    /** Everything the graph shows, ids and guard labels left out. */
    private static Map<String, Object> graph(AgentNode node) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("kind", node.kind());
        graph.put("name", node.name());
        graph.put("description", node.description());
        graph.put("inputKeys", node.inputKeys());
        graph.put("outputKey", node.outputKey());
        graph.put("optional", node.optional());
        graph.put("async", node.async());
        graph.put("guard", node.guard() == null ? null : List.of(String.valueOf(node.guard().maxIterations()),
                String.valueOf(node.guard().testExitAtLoopEnd())));
        graph.put("plannerInfo", node.plannerInfo());
        graph.put("aiDetails", node.aiDetails());
        graph.put("remoteDetails", node.remoteDetails());
        List<Object> children = new ArrayList<>();
        node.children().forEach(child -> children.add(graph(child)));
        graph.put("children", children);
        return graph;
    }

    private static AgentNode child(AgentNode node, int... path) {
        for (int index : path) {
            node = node.children().get(index);
        }
        return node;
    }

    @Test
    void builderTwinHasTheDeclarativeGraph() {
        AgenticSystem builder = system(builderStoryCreator);
        AgenticSystem twin = declarative.get("StoryCreator");

        assertEquals(graph(twin.root()), graph(builder.root()));
        assertEquals(twin.inputs(), builder.inputs(), "same run form");
        assertEquals(SystemOrigin.BUILDER, builder.origin());
        // only the guard label differs: the builder's exit condition is a lambda
        assertEquals("goodEnough", child(twin.root(), 1).guard().label());
        assertEquals(Guard.loopExit(BuilderTopology.LAMBDA, 5, true), child(builder.root(), 1).guard());
    }

    /** Node ids follow the rule run events are correlated with: the simple name of each agent's type. */
    @Test
    void nodeIdsFollowTheRunEventRule() {
        AgentNode root = system(builderStoryCreator).root();

        assertEquals("test#system", root.id());
        assertEquals(List.of("test#system/Drafter", "test#system/UntypedAgent"), root.children().stream().map(AgentNode::id).toList());
        assertEquals(List.of("test#system/UntypedAgent/StyleScorer", "test#system/UntypedAgent/StyleEditor"),
                child(root, 1).children().stream().map(AgentNode::id).toList());
    }

    /** The declarative system read at runtime: its live tree gives the build-time graph back, guard labels aside. */
    @Test
    void declarativeSystemReadAtRuntime() {
        AgentNode runtime = system(storyCreator).root();
        assertEquals(graph(declarative.get("StoryCreator").root()), graph(runtime));
        assertEquals("style score is at least 0.8", child(runtime, 1).guard().label());
    }

    @Test
    void lambdaGuardsAreLabelledLambda() {
        UntypedAgent experts = AgenticServices.conditionalBuilder().name("askExpert")
                .subAgents("category is medical", scope -> true, fresh(ExpertRouter.MedicalExpert.class))
                .subAgents(scope -> false, fresh(ExpertRouter.TechnicalExpert.class))
                .outputKey("response").build();

        AgentNode node = system(experts).root();
        assertEquals(NodeKind.CONDITIONAL, node.kind());
        assertEquals(Guard.activation("category is medical"), child(node, 0).guard());
        assertEquals(Guard.activation(BuilderTopology.LAMBDA), child(node, 1).guard());
    }

    /** Every node kind the builder API creates (A2A and MCP need a server: not covered). */
    @Test
    void everyBuilderNodeKind() {
        UntypedAgent parallel = AgenticServices.parallelBuilder().name("search")
                .subAgents(fresh(ResearchGatherer.WebSearcher.class), fresh(ResearchGatherer.DatabaseSearcher.class)).build();
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder().name("translateAll")
                .subAgents(fresh(PhraseTranslator.Translator.class)).itemsProvider("phrases").outputKey("translations").build();
        UntypedAgent loop = AgenticServices.loopBuilder().name("loop").subAgents(fresh(StoryWriter.IntroWriter.class)).maxIterations(2).build();
        UntypedAgent conditional = AgenticServices.conditionalBuilder().name("route")
                .subAgents(scope -> true, fresh(ExpertRouter.MedicalExpert.class)).build();
        var supervisor = AgenticServices.supervisorBuilder().name("transact").chatModel(chatModel)
                .subAgents(fresh(BankSupervisor.ExchangeAgent.class)).build();
        UntypedAgent planner = AgenticServices.plannerBuilder().name("write")
                .subAgents(fresh(DraftReviewer.Drafter.class), fresh(DraftReviewer.Reviewer.class)).planner(DraftReviewer.DraftReviewPlanner::new).build();
        var human = AgenticServices.humanInTheLoopBuilder().description("Asks for approval")
                .inputKey(String.class, "draft").outputKey("approval").responseProvider(scope -> "yes").build();
        var action = AgenticServices.agentAction(scope -> scope.writeState("done", true));
        UntypedAgent root = AgenticServices.sequenceBuilder().name("all")
                .subAgents(parallel, mapper, loop, conditional, supervisor, planner, human, action).build();

        AgentNode node = system(root).root();
        assertEquals(NodeKind.SEQUENCE, node.kind());
        assertEquals(List.of(NodeKind.PARALLEL, NodeKind.PARALLEL_MAPPER, NodeKind.LOOP, NodeKind.CONDITIONAL,
                NodeKind.SUPERVISOR, NodeKind.PLANNER, NodeKind.HUMAN_IN_THE_LOOP, NodeKind.NON_AI_AGENT),
                node.children().stream().map(AgentNode::kind).toList());
        assertEquals(NodeKind.AI_AGENT, child(node, 0, 0).kind());
        assertEquals("Search the web for: {{topic}}", child(node, 0, 0).aiDetails().userMessage());
        assertEquals(DraftReviewer.DraftReviewPlanner.class.getName(), child(node, 5).plannerInfo().plannerClass());
        assertEquals(PatternType.CUSTOM, child(node, 5).plannerInfo().patternType());
        // agents of a planner run in any order: the verdict the drafter reads comes from the reviewer
        assertEquals(List.of("topic"), child(node, 5).inputKeys());
        assertNull(child(node, 4).plannerInfo(), "a supervisor has no planner info, as at build time");
        assertEquals(List.of("draft"), child(node, 6).inputKeys());
        assertEquals("approval", child(node, 6).outputKey());
    }

    /**
     * A builder workflow over a declarative workflow is MIXED. langchain4j does not take a Quarkus declarative workflow
     * bean as a builder sub-agent ("Agent executor not found"), so the tree is a stub here.
     */
    @Test
    void builderOverADeclarativeWorkflowIsMixed() {
        StubAgent declarativeWorkflow = new StubAgent("create", StoryCreator.class,
                dev.langchain4j.agentic.workflow.impl.SequentialPlanner.class, List.of());
        StubAgent root = new StubAgent("root", UntypedAgent.class,
                dev.langchain4j.agentic.workflow.impl.SequentialPlanner.class, List.of(declarativeWorkflow));

        assertEquals(SystemOrigin.MIXED, BuilderTopology.system("id", "name", null, root).origin());
        assertEquals(SystemOrigin.BUILDER, system(builderStoryCreator).origin(), "agent beans as leaves are the norm");
    }

    record StubAgent(String name, Class<?> type, Class<? extends dev.langchain4j.agentic.planner.Planner> plannerType,
            List<AgentInstance> subagents) implements AgentInstance {

        @Override
        public String agentId() {
            return name;
        }

        @Override
        public String description() {
            return null;
        }

        @Override
        public java.lang.reflect.Type outputType() {
            return Object.class;
        }

        @Override
        public String outputKey() {
            return null;
        }

        @Override
        public boolean async() {
            return false;
        }

        @Override
        public List<dev.langchain4j.agentic.planner.AgentArgument> arguments() {
            return List.of();
        }

        @Override
        public AgentInstance parent() {
            return null;
        }

        @Override
        public dev.langchain4j.agentic.planner.AgenticSystemTopology topology() {
            return dev.langchain4j.agentic.planner.AgenticSystemTopology.SEQUENCE;
        }
    }
}
