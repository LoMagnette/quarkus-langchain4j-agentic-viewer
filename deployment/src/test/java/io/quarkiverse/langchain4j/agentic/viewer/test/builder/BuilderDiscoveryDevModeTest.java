package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * T5.1: builder systems produced as beans are listed with the declarative ones, created only when the Dev UI asks;
 * a produced sub-agent of another system is no root.
 */
public class BuilderDiscoveryDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(PipelineSystems.class, PipelineSystems.Greeter.class,
                    PipelineSystems.FixtureChatModel.class))
            .setLogRecordPredicate(record -> true);

    private static final String PIPELINE = PipelineSystems.class.getName() + "#pipeline";
    private static final String GREETER = PipelineSystems.class.getName() + "#greeter";

    public BuilderDiscoveryDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    private static boolean produced(String name) {
        return config.getLogRecords().stream().anyMatch(record -> ("producing " + name).equals(record.getMessage()));
    }

    @Test
    void builderSystemsListedWhenAsked() throws Exception {
        // nothing created at startup: the beans are only kept (the application injects none of them)
        assertTrue(!produced("pipeline") && !produced("greeter") && !produced("cleanup"), "no builder bean at startup");

        JsonNode systems = executeJsonRPCMethod("listSystems");
        List<String> ids = new ArrayList<>();
        systems.forEach(system -> ids.add(system.get("id").asText()));
        assertEquals(List.of(GREETER, PIPELINE), ids.stream().sorted().toList(), "cleanup is a sub-agent of pipeline");
        assertTrue(produced("pipeline") && produced("greeter"));

        JsonNode pipeline = null;
        for (JsonNode system : systems) {
            if (system.get("id").asText().equals(PIPELINE)) {
                pipeline = system;
            }
        }
        assertEquals("pipeline", pipeline.get("name").asText());
        assertEquals("BUILDER", pipeline.get("origin").asText());
        assertEquals("SEQUENCE", pipeline.get("rootKind").asText());
        assertEquals("pipeline", pipeline.get("sourceRef").get("methodName").asText());

        JsonNode topology = executeJsonRPCMethod("getTopology", Map.of("systemId", PIPELINE));
        List<String> kinds = new ArrayList<>();
        topology.get("root").get("children").forEach(child -> kinds.add(child.get("kind").asText()));
        assertEquals(List.of("NON_AI_AGENT", "LOOP"), kinds);
        assertEquals("Prepares, then cleans up", topology.get("root").get("description").asText());

        JsonNode greeter = executeJsonRPCMethod("getTopology", Map.of("systemId", GREETER));
        assertEquals("Greeter", greeter.get("name").asText(), "named after its interface");
    }
}
