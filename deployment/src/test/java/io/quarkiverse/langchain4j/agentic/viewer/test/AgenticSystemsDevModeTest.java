package io.quarkiverse.langchain4j.agentic.viewer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIBuildTimeDataTest;
import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * In dev mode, the Dev UI gets the agentic systems over JSON-RPC from the runtime-dev registry, filled with the
 * systems built at build time; the card shows their number.
 */
public class AgenticSystemsDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(StoryWorkflow.class));

    private static final String ROOT = StoryWorkflow.class.getName() + "#writeStory";

    public AgenticSystemsDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void listSystems() throws Exception {
        JsonNode systems = executeJsonRPCMethod("listSystems");

        assertEquals(1, systems.size(), systems::toString);
        JsonNode summary = systems.get(0);
        assertEquals(ROOT, summary.get("id").asText());
        assertEquals("StoryWorkflow", summary.get("name").asText());
        assertEquals("DECLARATIVE", summary.get("origin").asText());
        assertEquals("SEQUENCE", summary.get("rootKind").asText());
        assertEquals("[\"SEQUENCE\"]", summary.get("patternKinds").toString());
        assertEquals(1, summary.get("inputCount").asInt());
        assertEquals(StoryWorkflow.class.getName(), summary.get("sourceRef").get("className").asText());
    }

    @Test
    void getTopology() throws Exception {
        JsonNode system = executeJsonRPCMethod("getTopology", Map.of("systemId", ROOT));

        assertEquals(ROOT, system.get("rootNodeId").asText());
        List<String> childIds = new ArrayList<>();
        system.get("root").get("children").forEach(child -> childIds.add(child.get("id").asText()));
        assertEquals(List.of(ROOT + "/Writer", ROOT + "/Editor"), childIds);
        assertEquals("draft", system.get("root").get("children").get(0).get("outputKey").asText());
    }

    @Test
    void getInputSchema() throws Exception {
        JsonNode schema = executeJsonRPCMethod("getInputSchema", Map.of("systemId", ROOT));

        assertEquals("{\"type\":\"object\",\"properties\":{\"topic\":{\"type\":\"string\"}},\"required\":[\"topic\"]}",
                schema.toString());
    }

    @Test
    void startRunReturnsARunId() throws Exception {
        JsonNode runId = executeJsonRPCMethod("startRun", Map.of("systemId", ROOT, "inputs", Map.of("topic", "dragons")));

        assertEquals(36, runId.asText().length(), runId::toString);
    }

    @Test
    void unknownSystemIdIsAnError() throws Exception {
        String startRun = rawJsonRpc("quarkus-langchain4j-agentic-viewer_startRun",
                "{\"systemId\":\"com.example.Nope#run\",\"inputs\":{}}");
        assertTrue(startRun.contains("\"error\"") && startRun.contains("Unknown agentic system: com.example.Nope#run"),
                startRun);
        for (String method : List.of("getTopology", "getInputSchema")) {
            String response = rawJsonRpc("quarkus-langchain4j-agentic-viewer_" + method,
                    "{\"systemId\":\"com.example.Nope#run\"}");
            assertTrue(response.contains("\"error\"") && response.contains("Unknown agentic system: com.example.Nope#run"),
                    method + ": " + response);
        }
    }

    /**
     * Sends one JSON-RPC request over the Dev UI websocket and returns the raw response; DevUIJsonRPCTest only reads
     * successful results.
     */
    private String rawJsonRpc(String method, String params) throws Exception {
        int id = (int) (Math.random() * Integer.MAX_VALUE);
        CompletableFuture<String> response = new CompletableFuture<>();
        List<String> received = new CopyOnWriteArrayList<>();
        // uri is the .../dev-ui/json-rpc-ws endpoint
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(uri.toString().replaceFirst("^http", "ws")),
                        new WebSocket.Listener() {
                            private final StringBuilder text = new StringBuilder();

                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                text.append(data);
                                if (last) {
                                    received.add(text.toString());
                                    if (text.toString().matches("(?s).*\"id\"\\s*:\\s*\"?" + id + "\\b.*")) {
                                        response.complete(text.toString());
                                    }
                                    text.setLength(0);
                                }
                                webSocket.request(1);
                                return null;
                            }
                        })
                .get(10, TimeUnit.SECONDS);
        try {
            socket.sendText("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params
                    + "}", true).get(10, TimeUnit.SECONDS);
            try {
                return response.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("no response for id " + id + ", received: " + received, e);
            }
        } finally {
            socket.abort();
        }
    }

    @Test
    void cardShowsTheNumberOfSystems() throws Exception {
        // card pages are build-time data of the Dev UI itself
        JsonNode extensions = new DevUIBuildTimeDataTest("devui") {
        }.getBuildTimeData("extensions");

        List<String> labels = new ArrayList<>();
        extensions.findParents("title").forEach(page -> {
            if ("Agentic Systems".equals(page.path("title").asText()) && page.has("staticLabel")) {
                labels.add(page.path("staticLabel").asText().trim()); // stored with a leading space
            }
        });
        assertEquals(List.of("1"), labels, extensions::toString);
    }
}
