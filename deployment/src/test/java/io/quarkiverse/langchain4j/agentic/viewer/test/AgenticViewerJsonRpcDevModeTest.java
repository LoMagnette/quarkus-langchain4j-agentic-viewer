package io.quarkiverse.langchain4j.agentic.viewer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * In dev mode, the JSON-RPC service from runtime-dev answers over the Dev UI websocket,
 * with the graph discovered at build time from the real langchain4j-agentic annotations.
 */
public class AgenticViewerJsonRpcDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(StoryWorkflow.class));

    public AgenticViewerJsonRpcDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void agentCountIsServedFromRuntimeDev() throws Exception {
        JsonNode agentCount = executeJsonRPCMethod("getAgentCount");

        assertTrue(agentCount.asInt() >= 3, "Expected at least the sequence and its two sub-agents, got " + agentCount);
    }

    @Test
    void graphContainsRealAnnotationAgentsAndBuilderUsage() throws Exception {
        JsonNode graph = executeJsonRPCMethod("getGraph");

        Set<String> agentNames = new HashSet<>();
        Set<String> agentClasses = new HashSet<>();
        graph.get("agents").forEach(agent -> {
            agentNames.add(agent.get("name").asText());
            agentClasses.add(agent.get("className").asText());
        });
        assertTrue(agentNames.containsAll(Set.of("writeStory", "write", "edit")), "Agents found: " + agentNames);
        assertTrue(agentClasses.stream().noneMatch(className -> className.startsWith("dev.langchain4j.")),
                "Agents declared by the langchain4j libraries must not be listed: " + agentClasses);

        assertEquals(true, graph.get("hasImperativePatterns").asBoolean(), "Builder usage not detected: " + graph);
        Set<String> imperativeLocations = new HashSet<>();
        graph.get("imperativeLocations").forEach(location -> imperativeLocations.add(location.asText()));
        assertEquals(Set.of(StoryWorkflow.BuilderWorkflow.class.getName()), imperativeLocations);
    }
}
