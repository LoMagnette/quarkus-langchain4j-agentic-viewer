package io.quarkiverse.langchain4j.agentic.viewer.test.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.agentic.viewer.test.StoryWorkflow;
import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * quarkus.langchain4j.agentic.dev-ui.history-size and max-payload-size bound what the Dev UI keeps and shows of runs.
 */
public class RunConfigDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(StoryWorkflow.class).addAsResource(new StringAsset(
                    "quarkus.langchain4j.agentic.dev-ui.history-size=2\n"
                            + "quarkus.langchain4j.agentic.dev-ui.max-payload-size=40\n"),
                    "application.properties"));

    private static final String ROOT = StoryWorkflow.class.getName() + "#writeStory";

    public RunConfigDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    /** The fixture's runs end at once (no model provider): waits until a run is no longer RUNNING. */
    private JsonNode ended(String runId) throws Exception {
        for (int i = 0; i < 100; i++) {
            JsonNode trace = executeJsonRPCMethod("getRun", Map.of("runId", runId));
            if (!"RUNNING".equals(trace.path("summary").path("status").asText())) {
                return trace;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run still running: " + runId);
    }

    @Test
    void historySizeAndPayloadSize() throws Exception {
        String longTopic = "a very long topic ".repeat(20);
        List<String> runIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String runId = executeJsonRPCMethod("startRun", Map.of("systemId", ROOT, "inputs", Map.of("topic", longTopic)))
                    .asText();
            runIds.add(runId);
            JsonNode trace = ended(runId);
            String topic = trace.path("events").get(0).path("inputs").path("topic").asText();
            assertTrue(topic.contains("… [truncated, " + longTopic.length() + " chars]") && topic.length() < longTopic.length(),
                    "max-payload-size=40 truncates the input: " + topic);
        }
        List<String> kept = new ArrayList<>();
        executeJsonRPCMethod("listRuns", Map.of("systemId", ROOT)).forEach(run -> kept.add(run.path("runId").asText()));
        assertEquals(List.of(runIds.get(2), runIds.get(1)), kept, "history-size=2 keeps the 2 newest runs");
    }
}
