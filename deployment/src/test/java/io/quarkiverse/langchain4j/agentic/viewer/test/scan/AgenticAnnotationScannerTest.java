package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;

/**
 * Field-level checks of the descriptors, on a Jandex index of the fixtures.
 */
class AgenticAnnotationScannerTest {

    /** Not part of the app fixtures: @MemoryId needs chat memory at runtime. */
    interface WithMemory {
        @dev.langchain4j.agentic.Agent(outputKey = "reply")
        String chat(@MemoryId String memoryId, @V("question") String question);
    }

    private static final String P = ScanFixtures.class.getName() + "$";
    private static Map<String, AgentDescriptor> byId;

    @BeforeAll
    static void scan() throws Exception {
        Index index = Index.of(ScanFixtures.StoryFlow.class, ScanFixtures.Writer.class, ScanFixtures.Reviewer.class,
                ScanFixtures.ResearchFan.class, ScanFixtures.WebSearch.class, ScanFixtures.DbSearch.class,
                ScanFixtures.Batch.class, ScanFixtures.Translator.class, ScanFixtures.Polisher.class,
                ScanFixtures.Scorer.class, ScanFixtures.Editor.class, ScanFixtures.Router.class, ScanFixtures.Expert.class,
                ScanFixtures.Shouter.class, ScanFixtures.Bank.class, ScanFixtures.Teller.class,
                ScanFixtures.DraftPlanner.class, ScanFixtures.Approval.class, ScanFixtures.ApprovedStory.class,
                WithMemory.class);
        List<AgentDescriptor> descriptors = new AgenticAnnotationScanner().scan(index);
        byId = descriptors.stream().collect(Collectors.toMap(AgentDescriptor::id, Function.identity()));
        assertEquals(descriptors.stream().map(AgentDescriptor::id).sorted().toList(),
                descriptors.stream().map(AgentDescriptor::id).toList(), "sorted by id");
    }

    private static AgentDescriptor get(String type, String method) {
        AgentDescriptor descriptor = byId.get(P + type + "#" + method);
        assertTrue(descriptor != null, "no descriptor for " + type + "#" + method + " in " + byId.keySet());
        return descriptor;
    }

    @Test
    void sequence() {
        AgentDescriptor flow = get("StoryFlow", "write");
        assertEquals(NodeKind.SEQUENCE, flow.kind());
        assertEquals("storyFlow", flow.name());
        assertEquals("Writes then reviews", flow.description());
        assertEquals("review", flow.outputKey());
        assertEquals(List.of("topic"), flow.inputKeys());
        assertEquals(List.of(P + "Writer", P + "Reviewer"), flow.subAgents());
    }

    @Test
    void aiAgents() {
        AgentDescriptor writer = get("Writer", "write");
        assertEquals(NodeKind.AI_AGENT, writer.kind());
        assertEquals("write", writer.name(), "method name when no name is set");
        assertEquals("Writes a draft", writer.description(), "@Agent value is the description");
        assertEquals("draft", writer.outputKey());

        AgentDescriptor reviewer = get("Reviewer", "review");
        assertEquals("reviewer", reviewer.name());
        assertEquals(List.of("draft"), reviewer.inputKeys(), "AgenticScope parameter skipped");
        assertEquals(List.of(), reviewer.subAgents());
    }

    @Test
    void memoryIdParameterIsNotAnInputKey() {
        assertEquals(List.of("question"), byId.get(AgenticAnnotationScannerTest.class.getName() + "$WithMemory#chat")
                .inputKeys());
    }

    @Test
    void parallel() {
        AgentDescriptor fan = get("ResearchFan", "research");
        assertEquals(NodeKind.PARALLEL, fan.kind());
        assertEquals(List.of(P + "WebSearch", P + "DbSearch"), fan.subAgents());
        assertNull(fan.description());
    }

    @Test
    void parallelMapper() {
        AgentDescriptor batch = get("Batch", "translateAll");
        assertEquals(NodeKind.PARALLEL_MAPPER, batch.kind());
        assertEquals(List.of(P + "Translator"), batch.subAgents());
        assertEquals("items", batch.itemsProvider());
        assertEquals("translations", batch.outputKey());
    }

    @Test
    void loopAndTypedOutputKey() {
        AgentDescriptor polisher = get("Polisher", "polish");
        assertEquals(NodeKind.LOOP, polisher.kind());
        assertEquals(List.of(P + "Scorer", P + "Editor"), polisher.subAgents());
        assertEquals("Score", get("Scorer", "score").outputKey(), "typed key name is its simple class name");
    }

    @Test
    void conditional() {
        AgentDescriptor router = get("Router", "route");
        assertEquals(NodeKind.CONDITIONAL, router.kind());
        assertEquals(List.of(P + "Expert"), router.subAgents());
        assertNull(router.itemsProvider());
    }

    @Test
    void nonAiAgent() {
        AgentDescriptor shouter = get("Shouter", "shout");
        assertEquals(NodeKind.NON_AI_AGENT, shouter.kind());
        assertEquals("Upper-cases the draft", shouter.description());
        assertEquals(List.of("draft"), shouter.inputKeys());
    }

    @Test
    void supervisor() {
        AgentDescriptor bank = get("Bank", "transact");
        assertEquals(NodeKind.SUPERVISOR, bank.kind());
        assertEquals("Banking supervisor", bank.description());
        assertEquals(List.of(P + "Teller"), bank.subAgents());
        assertEquals(List.of("request"), bank.inputKeys());
        assertNull(bank.plannerSupplier());
    }

    @Test
    void plannerWithSupplier() {
        AgentDescriptor planner = get("DraftPlanner", "plan");
        assertEquals(NodeKind.PLANNER, planner.kind());
        assertEquals(List.of(P + "Writer"), planner.subAgents());
        assertEquals(P + "DraftPlanner#planner", planner.plannerSupplier());
        assertTrue(byId.keySet().stream().noneMatch(id -> id.endsWith("#planner")), "the supplier is not an agent");
    }

    @Test
    void humanInTheLoop() {
        AgentDescriptor approval = get("Approval", "ask");
        assertEquals(NodeKind.HUMAN_IN_THE_LOOP, approval.kind());
        assertEquals("Asks for approval", approval.description(), "@HumanInTheLoop value is the description");
        assertEquals("approval", approval.outputKey());
        assertEquals(List.of("draft"), approval.inputKeys());
        assertEquals(List.of(P + "Writer", P + "Approval"), get("ApprovedStory", "write").subAgents());
    }

    @Test
    void onlyAgentMethods() {
        assertEquals(20, byId.size(), byId.keySet()::toString);
    }
}
