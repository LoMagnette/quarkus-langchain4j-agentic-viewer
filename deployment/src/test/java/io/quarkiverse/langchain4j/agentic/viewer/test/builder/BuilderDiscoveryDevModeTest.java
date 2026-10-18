package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                    PipelineSystems.FixtureChatModel.class, FieldSystems.class, DelegatingWorkflow.class,
                    DelegatingWorkflow.Step.class, DelegatingWorkflow.EntryPoint.class))
            .setLogRecordPredicate(record -> true);

    private static final String PIPELINE = PipelineSystems.class.getName() + "#pipeline";
    private static final String GREETER = PipelineSystems.class.getName() + "#greeter";
    private static final String DELEGATING = DelegatingWorkflow.class.getName() + "#run";

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
        // cleanup is a sub-agent of pipeline; the systems of FieldSystems are no beans (builder-discovery=transform only)
        assertEquals(List.of(DELEGATING, GREETER, PIPELINE), ids.stream().sorted().toList());
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
        // T5.5b: a declarative workflow whose step injects a builder system is MIXED
        for (JsonNode system : systems) {
            if (system.get("id").asText().equals(DELEGATING)) {
                assertEquals("MIXED", system.get("origin").asText());
            }
        }

        JsonNode topology = executeJsonRPCMethod("getTopology", Map.of("systemId", PIPELINE));
        List<String> kinds = new ArrayList<>();
        topology.get("root").get("children").forEach(child -> kinds.add(child.get("kind").asText()));
        assertEquals(List.of("NON_AI_AGENT", "LOOP"), kinds);
        assertEquals("Prepares, then cleans up", topology.get("root").get("description").asText());

        JsonNode greeter = executeJsonRPCMethod("getTopology", Map.of("systemId", GREETER));
        assertEquals("Greeter", greeter.get("name").asText(), "named after its interface");
    }

    /** T5.6: the form of a typed builder system comes from its interface method; builder systems run. */
    @Test
    void builderSystemsRun() throws Exception {
        assertEquals("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}",
                executeJsonRPCMethod("getInputSchema", Map.of("systemId", GREETER)).toString());

        // the declarative DelegatingWorkflow is MIXED (its step injects pipeline): it runs as a declarative system
        for (String system : List.of(PIPELINE, GREETER, DELEGATING)) {
            String runId = executeJsonRPCMethod("startRun", Map.of("systemId", system, "inputs",
                    Map.of("name", "Ada", "topic", "dragons"))).asText();
            JsonNode summary = null;
            for (int i = 0; i < 100 && (summary == null || "RUNNING".equals(summary.path("status").asText())); i++) {
                Thread.sleep(100);
                for (JsonNode run : executeJsonRPCMethod("listRuns", Map.of("systemId", system))) {
                    if (runId.equals(run.path("runId").asText())) {
                        summary = run;
                    }
                }
            }
            assertNotNull(summary, "run " + runId + " of " + system + " not found in the history");
            JsonNode trace = executeJsonRPCMethod("getRun", Map.of("runId", runId));
            String status = summary.path("status").asText();
            assertEquals("SUCCEEDED", status, () -> system + ": " + trace);
            if (system.equals(DELEGATING)) {
                // the pipeline its step runs is reported under the step, not as the run's root again
                List<String> started = new ArrayList<>();
                trace.path("events").forEach(event -> {
                    if ("AgentStarted".equals(event.path("type").asText())) {
                        started.add(event.path("nodeId").asText().substring(DELEGATING.length()));
                    }
                });
                assertEquals(List.of("", "/Step", "/Step/UntypedAgent", "/Step/UntypedAgent/AgenticScopeAction",
                        "/Step/UntypedAgent/UntypedAgent", "/Step/UntypedAgent/UntypedAgent/AgenticScopeAction",
                        "/Step/UntypedAgent/UntypedAgent/AgenticScopeAction"), started, trace::toString);
            }
        }
    }
}
