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

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.RemoteAgentDetails;

/**
 * Remote agents (A2A and MCP client agents) used as sub-agents of a workflow. Index only: the A2A and MCP runtime
 * modules of langchain4j-agentic are not dependencies of the extension, so these fixtures are not built as an app.
 */
class RemoteAgentsTest {

    interface RemoteFlow {
        @SequenceAgent(outputKey = "summary", subAgents = { Translator.class, Reviewer.class, Search.class })
        String run(@V("text") String text);
    }

    interface Translator {
        @A2AClientAgent(a2aServerUrl = "http://localhost:8081", tenant = "acme", description = "Translates the text",
                outputKey = "translation")
        String translate(@V("text") String text);
    }

    interface Reviewer {
        @A2AClientAgent(name = "reviewer", outputKey = "review", async = true)
        String review(@V("translation") String translation);

        @A2AServerUrlSupplier
        static String serverUrl() {
            return "http://reviews:8080";
        }
    }

    interface Search {
        @McpClientAgent(toolName = "web_search", outputKey = "summary")
        String search(@V("review") String query);

        @McpClientSupplier
        static Object client() {
            return null;
        }
    }

    private static final String P = RemoteAgentsTest.class.getName() + "$";
    private static Map<String, AgentDescriptor> byId;
    private static AgenticSystem system;

    @BeforeAll
    static void scan() throws Exception {
        List<AgentDescriptor> descriptors = new AgenticAnnotationScanner()
                .scan(Index.of(RemoteFlow.class, Translator.class, Reviewer.class, Search.class));
        byId = descriptors.stream().collect(Collectors.toMap(AgentDescriptor::id, Function.identity()));
        List<AgenticSystem> systems = new TopologyBuilder().build(new RootSystemDetector().detect(descriptors));
        assertEquals(1, systems.size(), "remote agents are sub-agents, not systems");
        system = systems.get(0);
    }

    @Test
    void a2aAgentWithServerUrl() {
        AgentDescriptor translator = byId.get(P + "Translator#translate");
        assertEquals(NodeKind.A2A_CLIENT_AGENT, translator.kind());
        assertEquals("Translates the text", translator.description());
        assertEquals(List.of("text"), translator.inputKeys());
        assertEquals("translation", translator.outputKey());
        assertEquals(new RemoteAgentDetails("http://localhost:8081", null, "acme", null, null), translator.remoteDetails());
        assertNull(translator.aiDetails(), "the model runs on the remote server");
        assertNull(translator.outputMethod(), "not a workflow");
    }

    @Test
    void a2aAgentWithServerUrlSupplier() {
        AgentDescriptor reviewer = byId.get(P + "Reviewer#review");
        assertEquals("reviewer", reviewer.name());
        assertTrue(reviewer.async());
        assertEquals(new RemoteAgentDetails(null, P + "Reviewer#serverUrl", null, null, null), reviewer.remoteDetails());
        assertTrue(byId.keySet().stream().noneMatch(id -> id.endsWith("#serverUrl")), "the supplier is not an agent");
    }

    @Test
    void mcpClientAgent() {
        AgentDescriptor search = byId.get(P + "Search#search");
        assertEquals(NodeKind.MCP_CLIENT_AGENT, search.kind());
        assertEquals(List.of("review"), search.inputKeys());
        assertEquals(new RemoteAgentDetails(null, null, null, "web_search", P + "Search#client"), search.remoteDetails());
    }

    @Test
    void remoteAgentsAreNodesOfTheWorkflow() {
        List<AgentNode> children = system.root().children();
        assertEquals(List.of(NodeKind.A2A_CLIENT_AGENT, NodeKind.A2A_CLIENT_AGENT, NodeKind.MCP_CLIENT_AGENT),
                children.stream().map(AgentNode::kind).toList(), "resolved, not unresolved leaves");
        assertEquals(P + "RemoteFlow#run/Translator", children.get(0).id());
        assertEquals("acme", children.get(0).remoteDetails().tenant());
        assertEquals("web_search", children.get(2).remoteDetails().toolName());
        assertNull(system.root().remoteDetails());
    }
}
