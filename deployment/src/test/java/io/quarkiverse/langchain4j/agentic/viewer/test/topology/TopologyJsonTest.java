package io.quarkiverse.langchain4j.agentic.viewer.test.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;

/**
 * The topology model round-trips through a default Jackson ObjectMapper (as used by Dev UI JSON-RPC),
 * with plain JSON field names the UI can read without custom code.
 */
class TopologyJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTrip() throws Exception {
        for (AgenticSystem system : List.of(TopologyFixtures.storyCreator(), TopologyFixtures.routerWithPlanner())) {
            String json = mapper.writeValueAsString(system);
            assertEquals(system, mapper.readValue(json, AgenticSystem.class), json);
        }
    }

    @Test
    void plainJsonShape() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(TopologyFixtures.storyCreator()));

        assertEquals("StoryCreator.create", json.get("rootNodeId").asText());
        assertEquals("DECLARATIVE", json.get("origin").asText());
        assertEquals("topic", json.get("inputs").get(0).get("name").asText());
        JsonNode loop = json.get("root").get("children").get(1);
        assertEquals("LOOP", loop.get("kind").asText());
        assertEquals("goodEnough", loop.get("guard").get("label").asText());
        assertEquals(5, loop.get("guard").get("maxIterations").asInt());
        assertEquals(true, loop.get("guard").get("testExitAtLoopEnd").asBoolean());
        assertEquals("score", loop.get("children").get(0).get("name").asText());
    }

    @Test
    void rootNodeIdMustMatchRoot() {
        AgentNode root = new AgentNode("a", "a", NodeKind.AI_AGENT, null, null, null, false, false, null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> new AgenticSystem("s", "s", "other", SystemOrigin.DECLARATIVE, null, null, root));
    }

    @Test
    void nullListsBecomeEmpty() {
        AgentNode node = new AgentNode("a", "a", NodeKind.AI_AGENT, null, null, null, false, false, null, null, null, null);
        assertEquals(List.of(), node.inputKeys());
        assertEquals(List.of(), node.children());
    }
}
