package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AiAgentDetails;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.ToolDescriptor;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
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

    /** Not part of the app fixtures: one condition shared by two branches, and a branch langchain4j drops. */
    interface Triage {
        @ConditionalAgent(outputKey = "answer", subAgents = { ScanFixtures.Expert.class, ScanFixtures.Generalist.class,
                ScanFixtures.Writer.class })
        String triage(@V("topic") String topic);

        @ActivationCondition({ ScanFixtures.Expert.class, ScanFixtures.Generalist.class })
        static boolean answerable(@V("topic") String topic) {
            return true;
        }
    }

    /** Not part of the app fixtures: tools only known at runtime, method-level system message over the class one. */
    @SystemMessage("Class-level, overridden")
    interface Exchange {
        @dev.langchain4j.agentic.Agent(outputKey = "usd")
        @SystemMessage({ "You convert money.", "Be exact." })
        @McpToolBox({ "rates", "fees" })
        String convert(@V("amount") double amount);

        @ToolsSupplier
        static Object tools() {
            return new Object();
        }

        @ToolProviderSupplier
        static Object toolProvider() {
            return null;
        }
    }

    /** Not part of the app fixtures: bare AI agent, and MCP tools from every client. */
    interface Bare {
        @dev.langchain4j.agentic.Agent
        @McpToolBox
        String reply(@V("question") String question);
    }

    /** Not part of the app fixtures: asynchronous human in the loop. */
    interface AsyncApproval {
        @HumanInTheLoop(outputKey = "approval", async = true)
        static String ask(@V("draft") String draft) {
            return "yes";
        }
    }

    private static final String P = ScanFixtures.class.getName() + "$";
    private static final String T = AgenticAnnotationScannerTest.class.getName() + "$";
    private static Map<String, AgentDescriptor> byId;

    @BeforeAll
    static void scan() throws Exception {
        Index index = Index.of(ScanFixtures.StoryFlow.class, ScanFixtures.Writer.class, ScanFixtures.Reviewer.class,
                ScanFixtures.ResearchFan.class, ScanFixtures.WebSearch.class, ScanFixtures.DbSearch.class,
                ScanFixtures.Batch.class, ScanFixtures.Translator.class, ScanFixtures.Polisher.class,
                ScanFixtures.Scorer.class, ScanFixtures.Editor.class, ScanFixtures.Repeater.class, ScanFixtures.Router.class,
                ScanFixtures.Expert.class, ScanFixtures.Generalist.class, ScanFixtures.Shouter.class, ScanFixtures.Bank.class, ScanFixtures.Teller.class,
                ScanFixtures.DraftPlanner.class, ScanFixtures.Approval.class, ScanFixtures.ApprovedStory.class,
                ScanFixtures.Cashier.class, ScanFixtures.Ledger.class, ScanFixtures.AuditTools.class,
                WithMemory.class, Triage.class, Exchange.class, Bare.class, AsyncApproval.class);
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
    void loopGuardAndOutput() {
        AgentDescriptor polisher = get("Polisher", "polish");
        assertEquals(Guard.loopExit("goodEnough", 3, true), polisher.loopGuard());
        assertEquals(P + "Polisher#finalDraft", polisher.outputMethod());
        assertEquals(Map.of(), polisher.branchGuards());
        assertTrue(byId.keySet().stream().noneMatch(id -> id.endsWith("#goodEnough") || id.endsWith("#finalDraft")),
                "guards and @Output methods are not agents");
    }

    @Test
    void loopDefaults() {
        AgentDescriptor repeater = get("Repeater", "repeat");
        assertEquals(Guard.loopExit(null, 10, false), repeater.loopGuard(), "no exit condition, default maxIterations");
        assertNull(repeater.outputMethod());
    }

    @Test
    void conditional() {
        AgentDescriptor router = get("Router", "route");
        assertEquals(NodeKind.CONDITIONAL, router.kind());
        assertEquals(List.of(P + "Expert", P + "Generalist"), router.subAgents());
        assertNull(router.itemsProvider());
        assertNull(router.loopGuard());
    }

    @Test
    void conditionalBranchGuards() {
        assertEquals(List.of(P + "Expert", P + "Generalist"), List.copyOf(get("Router", "route").branchGuards().keySet()),
                "sub-agent order");
        assertEquals(Map.of(P + "Expert", Guard.activation("technical"), P + "Generalist", Guard.activation("general")),
                get("Router", "route").branchGuards());

        AgentDescriptor triage = byId.get(AgenticAnnotationScannerTest.class.getName() + "$Triage#triage");
        assertEquals(Map.of(P + "Expert", Guard.activation("answerable"), P + "Generalist", Guard.activation("answerable")),
                triage.branchGuards(), "a shared condition guards each listed branch; Writer has none");
        assertEquals(3, triage.subAgents().size(), "an unguarded branch stays a sub-agent");
    }

    @Test
    void guardsOnlyOnTheirKind() {
        AgentDescriptor flow = get("StoryFlow", "write");
        assertNull(flow.loopGuard());
        assertEquals(Map.of(), flow.branchGuards());
        assertNull(flow.outputMethod());
        assertNull(get("Writer", "write").outputMethod());
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
    void aiAgentDetails() {
        AgentDescriptor cashier = get("Cashier", "pay");
        assertEquals(NodeKind.AI_AGENT, cashier.kind());
        assertEquals("cashier", cashier.name());
        assertEquals("Pays out cash", cashier.description());
        assertTrue(cashier.optional());
        assertTrue(cashier.async());
        AiAgentDetails details = cashier.aiDetails();
        assertEquals("You are a careful cashier.\nNever pay more than asked.\n", details.systemMessage(),
                "class-level @SystemMessage read from its resource");
        assertEquals("Pay {{amount}} EUR to {{customer}}", details.userMessage(), "lines joined with the delimiter");
        assertEquals("fast", details.modelName());
        assertEquals(List.of(
                new ToolDescriptor("balance_of", P + "Ledger", "balance", "Reads the balance\nof a customer"),
                new ToolDescriptor("debit", P + "Ledger", "debit", null),
                new ToolDescriptor("audit", P + "AuditTools", "audit", "Records an audit entry")),
                details.tools(), "by method name, inherited tools last; notATool skipped");
        assertEquals(List.of(), details.runtimeToolSources());
    }

    @Test
    void runtimeToolSourcesAndMethodSystemMessage() {
        AiAgentDetails details = byId.get(T + "Exchange#convert").aiDetails();
        assertEquals("You convert money.\nBe exact.", details.systemMessage(), "method wins, default delimiter");
        assertNull(details.userMessage());
        assertNull(details.modelName(), "default model");
        assertEquals(List.of(), details.tools());
        assertEquals(List.of(T + "Exchange#tools", T + "Exchange#toolProvider", "mcp:rates", "mcp:fees"),
                details.runtimeToolSources());

        AgentDescriptor bare = byId.get(T + "Bare#reply");
        assertEquals(List.of("mcp:*"), bare.aiDetails().runtimeToolSources());
        assertNull(bare.aiDetails().systemMessage());
        assertFalse(bare.optional());
        assertFalse(bare.async());
    }

    @Test
    void flagsAndDetailsOutsideAiAgents() {
        AgentDescriptor approval = byId.get(T + "AsyncApproval#ask");
        assertTrue(approval.async(), "@HumanInTheLoop(async)");
        assertNull(approval.aiDetails());
        assertNull(get("Shouter", "shout").aiDetails(), "non-AI agent");
        assertNull(get("StoryFlow", "write").aiDetails(), "workflow");
        assertFalse(get("StoryFlow", "write").async());
    }

    @Test
    void onlyAgentMethods() {
        assertEquals(27, byId.size(), byId.keySet()::toString);
    }
}
