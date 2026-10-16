package io.quarkiverse.langchain4j.agentic.viewer.test.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PlannerInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;
import io.quarkiverse.langchain4j.agentic.viewer.test.scan.ScanFixtures;

/**
 * Declarative topologies built from a scan of the fixtures, one test per node kind.
 */
class TopologyBuilderTest {

    /** Not part of the app fixtures: a loop nested in a sequence. */
    interface PolishedStory {
        @SequenceAgent(outputKey = "draft", subAgents = { ScanFixtures.Writer.class, ScanFixtures.Polisher.class })
        String write(@V("topic") String topic);
    }

    /** Not part of the app fixtures: the same sub-agent twice, and a class without agent method. */
    interface TwiceEdited {
        @SequenceAgent(outputKey = "draft", subAgents = { ScanFixtures.Editor.class, ScanFixtures.Editor.class,
                NotAnAgent.class })
        String edit(@V("draft") String draft);
    }

    interface NotAnAgent {
    }

    private static final String P = ScanFixtures.class.getName() + "$";
    private static final String T = TopologyBuilderTest.class.getName() + "$";
    private static Map<String, AgenticSystem> systems;

    private static List<AgenticSystem> build() throws Exception {
        Index index = Index.of(ScanFixtures.class.getDeclaredClasses());
        Index extra = Index.of(PolishedStory.class, TwiceEdited.class, NotAnAgent.class);
        List<io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor> descriptors = new ArrayList<>(
                new AgenticAnnotationScanner().scan(index));
        descriptors.addAll(new AgenticAnnotationScanner().scan(extra));
        return new TopologyBuilder().build(new RootSystemDetector().detect(descriptors));
    }

    @BeforeAll
    static void buildAll() throws Exception {
        systems = build().stream().collect(Collectors.toMap(AgenticSystem::id, Function.identity()));
    }

    private static AgenticSystem system(String id) {
        AgenticSystem system = systems.get(id);
        assertTrue(system != null, "no system " + id + " in " + systems.keySet());
        return system;
    }

    private static AgentNode child(AgentNode parent, int index) {
        return parent.children().get(index);
    }

    @Test
    void declarativeSystem() {
        AgenticSystem system = system(P + "StoryFlow#write");
        assertEquals("StoryFlow", system.name());
        assertEquals(SystemOrigin.DECLARATIVE, system.origin());
        assertEquals(new SourceRef(P + "StoryFlow", "write", null), system.sourceRef());
        assertEquals(system.id(), system.rootNodeId());
        assertEquals(List.of(new SystemInput("topic", "java.lang.String", "{\"type\":\"string\"}", true)), system.inputs());
    }

    @Test
    void sequenceWithAiAgents() {
        AgentNode flow = system(P + "StoryFlow#write").root();
        assertEquals(NodeKind.SEQUENCE, flow.kind());
        assertEquals("storyFlow", flow.name());
        assertEquals("Writes then reviews", flow.description());
        assertEquals(List.of("topic"), flow.inputKeys());
        assertEquals("review", flow.outputKey());
        assertEquals(List.of(P + "StoryFlow#write/Writer", P + "StoryFlow#write/Reviewer"),
                flow.children().stream().map(AgentNode::id).toList());
        AgentNode reviewer = child(flow, 1);
        assertEquals(NodeKind.AI_AGENT, reviewer.kind());
        assertEquals("reviewer", reviewer.name());
        assertEquals(List.of("draft"), reviewer.inputKeys());
        assertNull(reviewer.guard());
    }

    @Test
    void aiAgentDetailsAndFlags() {
        AgentNode cashier = system(P + "Cashier#pay").root();
        assertEquals(NodeKind.AI_AGENT, cashier.kind());
        assertTrue(cashier.optional());
        assertTrue(cashier.async());
        assertEquals("fast", cashier.aiDetails().modelName());
        assertEquals(List.of("balance_of", "debit", "audit"),
                cashier.aiDetails().tools().stream().map(t -> t.name()).toList());
    }

    @Test
    void nonAiAgent() {
        AgentNode shouter = system(P + "Shouter#shout").root();
        assertEquals(NodeKind.NON_AI_AGENT, shouter.kind());
        assertNull(shouter.aiDetails());
    }

    @Test
    void parallel() {
        AgentNode fan = system(P + "ResearchFan#research").root();
        assertEquals(NodeKind.PARALLEL, fan.kind());
        assertEquals(List.of("searchWeb", "searchDb"), fan.children().stream().map(AgentNode::name).toList());
    }

    @Test
    void parallelMapper() {
        AgentNode batch = system(P + "Batch#translateAll").root();
        assertEquals(NodeKind.PARALLEL_MAPPER, batch.kind());
        assertEquals(P + "Batch#translateAll/Translator", child(batch, 0).id());
    }

    @Test
    void loopWithExitGuard() {
        // Polisher is nested in PolishedStory here, so the standalone loop is Repeater
        AgentNode repeater = system(P + "Repeater#repeat").root();
        assertEquals(NodeKind.LOOP, repeater.kind());
        assertEquals(Guard.loopExit(null, 10, false), repeater.guard());
        assertEquals(List.of("edit"), repeater.children().stream().map(AgentNode::name).toList());
    }

    @Test
    void loopInsideSequence() {
        AgentNode story = system(T + "PolishedStory#write").root();
        AgentNode loop = child(story, 1);
        assertEquals(T + "PolishedStory#write/Polisher", loop.id());
        assertEquals(NodeKind.LOOP, loop.kind());
        assertEquals(Guard.loopExit("goodEnough", 3, true), loop.guard());
        assertEquals(List.of(T + "PolishedStory#write/Polisher/Scorer", T + "PolishedStory#write/Polisher/Editor"),
                loop.children().stream().map(AgentNode::id).toList());
        assertEquals("Score", child(loop, 0).outputKey());
        assertEquals(List.of(T + "PolishedStory#write"), systems.keySet().stream()
                .filter(id -> id.contains("Polish")).toList(), "the nested loop is not a system of its own");
    }

    @Test
    void conditionalBranchesCarryTheirActivationGuard() {
        AgentNode router = system(P + "Router#route").root();
        assertEquals(NodeKind.CONDITIONAL, router.kind());
        assertNull(router.guard());
        assertEquals(List.of(Guard.activation("technical"), Guard.activation("general")),
                router.children().stream().map(AgentNode::guard).toList());
    }

    @Test
    void supervisor() {
        AgentNode bank = system(P + "Bank#transact").root();
        assertEquals(NodeKind.SUPERVISOR, bank.kind());
        assertNull(bank.plannerInfo());
        assertEquals("move", child(bank, 0).name());
    }

    @Test
    void plannerReferencesItsSupplier() {
        AgentNode planner = system(P + "DraftPlanner#plan").root();
        assertEquals(NodeKind.PLANNER, planner.kind());
        assertEquals(new PlannerInfo(null, PatternType.CUSTOM, Map.of("supplier", P + "DraftPlanner#planner")),
                planner.plannerInfo());
    }

    @Test
    void humanInTheLoop() {
        AgentNode approval = child(system(P + "ApprovedStory#write").root(), 1);
        assertEquals(NodeKind.HUMAN_IN_THE_LOOP, approval.kind());
        assertEquals("Asks for approval", approval.description());
    }

    @Test
    void sharedSubAgentHasOneNodePerParent() {
        assertEquals(P + "DraftPlanner#plan/Writer", child(system(P + "DraftPlanner#plan").root(), 0).id());
        assertEquals(P + "ApprovedStory#write/Writer", child(system(P + "ApprovedStory#write").root(), 0).id());
    }

    @Test
    void repeatedSubAgentAndUnresolvedClass() {
        AgentNode edited = system(T + "TwiceEdited#edit").root();
        assertEquals(List.of(T + "TwiceEdited#edit/Editor", T + "TwiceEdited#edit/Editor~2",
                T + "TwiceEdited#edit/NotAnAgent"), edited.children().stream().map(AgentNode::id).toList());
        AgentNode unresolved = child(edited, 2);
        assertEquals("NotAnAgent", unresolved.name());
        assertEquals(NodeKind.NON_AI_AGENT, unresolved.kind());
        assertEquals("No agent method found in " + T + "NotAnAgent", unresolved.description());
    }

    @Test
    void nodeIdsAreUniqueAndStableAcrossBuilds() throws Exception {
        for (AgenticSystem system : systems.values()) {
            Set<String> ids = new HashSet<>();
            collectIds(system.root(), ids);
        }
        assertEquals(List.copyOf(systems.values()).stream().sorted((a, b) -> a.id().compareTo(b.id())).toList(),
                build().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList(),
                "a second scan and build of the same classes gives equal systems");
    }

    private static void collectIds(AgentNode node, Set<String> ids) {
        assertTrue(ids.add(node.id()), "duplicate node id " + node.id());
        node.children().forEach(child -> collectIds(child, ids));
    }
}
