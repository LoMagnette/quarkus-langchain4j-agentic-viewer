package io.quarkiverse.langchain4j.agentic.viewer.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgentParameter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgenticGraph;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.CompositionType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.ToolNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.ToolParameter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgentType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.WorkflowComposition;

/**
 * Unit tests for the Agentic Viewer model classes.
 */
public class AgenticViewerDevModeTest {

    @Test
    void testGraphDiscovery() {
        // Verify that the graph structure is correct
        AgenticGraph emptyGraph = AgenticGraph.empty();
        assertNotNull(emptyGraph);
        assertNotNull(emptyGraph.agents());
        assertNotNull(emptyGraph.tools());
        assertNotNull(emptyGraph.dataFlows());
        assertNotNull(emptyGraph.compositions());
        assertFalse(emptyGraph.hasImperativePatterns());
    }

    @Test
    void testAgentNodeStructure() {
        AgentNode agent = new AgentNode(
                "test_id",
                "TestAgent",
                AgentType.AGENT,
                "Test description",
                "System message",
                "User message",
                "outputKey",
                List.of("tool1", "tool2"),
                "com.example.TestAgent",
                "testMethod",
                false,
                null,
                List.of());

        assertNotNull(agent);
        assertTrue(agent.name().equals("TestAgent"));
        assertTrue(agent.toolIds().size() == 2);
    }

    @Test
    void testToolNodeStructure() {
        ToolNode tool = new ToolNode(
                "tool_id",
                "searchTool",
                "Search for information",
                "com.example.Tools",
                "search",
                List.of(new ToolParameter("query", "String")),
                "String");

        assertNotNull(tool);
        assertTrue(tool.name().equals("searchTool"));
        assertTrue(tool.parameters().size() == 1);
    }

    @Test
    void testGraphWithImperativePatterns() {
        AgenticGraph graph = AgenticGraph.empty();
        AgenticGraph enhancedGraph = graph.withImperativePatterns(true, List.of("com.example.MyClass"));

        assertTrue(enhancedGraph.hasImperativePatterns());
        assertTrue(enhancedGraph.imperativeLocations().contains("com.example.MyClass"));
    }

    @Test
    void testGraphWithAdditionalAgentsAndCompositions() {
        // Start with a graph that has two existing agents
        AgentNode agent1 = new AgentNode("a1", "Step1", AgentType.AGENT, "Step 1", null, null, null,
                List.of(), "com.example.Step1", "run", false, null, List.of());
        AgentNode agent2 = new AgentNode("a2", "Step2", AgentType.AGENT, "Step 2", null, null, null,
                List.of(), "com.example.Step2", "run", false, null, List.of());

        AgenticGraph graph = new AgenticGraph(
                List.of(agent1, agent2), List.of(), List.of(), List.of(),
                false, List.of(), List.of());

        // Simulate bridge: add a synthetic parent with a composition
        AgentNode syntheticParent = new AgentNode("imperative_1", "Workflow", AgentType.SEQUENCE,
                "Imperative sequence workflow", null, null, null, List.of(),
                "com.example.Workflow", "create", false, null, List.of());
        WorkflowComposition composition = new WorkflowComposition(
                "imperative_1", List.of("a1", "a2"), CompositionType.SEQUENCE);

        AgenticGraph bridgedGraph = graph.withAdditionalAgentsAndCompositions(
                List.of(syntheticParent), List.of(composition));

        assertEquals(3, bridgedGraph.agents().size());
        assertEquals(1, bridgedGraph.compositions().size());
        assertEquals("imperative_1", bridgedGraph.compositions().get(0).parentAgentId());
        assertEquals(List.of("a1", "a2"), bridgedGraph.compositions().get(0).childAgentIds());
        assertEquals(CompositionType.SEQUENCE, bridgedGraph.compositions().get(0).type());
    }
}
