package io.quarkiverse.langchain4j.agentic.viewer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            .withApplicationRoot(jar -> jar.addClasses(StoryWorkflow.class))
            .setLogRecordPredicate(record -> true);

    private static final String ROOT = StoryWorkflow.class.getName() + "#writeStory";
    /** The Dev UI JSON-RPC websocket of the dev-mode test application (DevUIJsonRPCTest keeps its own private). */
    private static final String JSON_RPC_WS = "ws://localhost:8080/q/dev-ui/json-rpc-ws";

    public AgenticSystemsDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    /** The internal parts of langchain4j-agentic the live view relies on are all there: no startup warning. */
    @Test
    void noInternalApiWarning() {
        assertTrue(config.getLogRecords().stream().noneMatch(record -> String.valueOf(record.getMessage())
                .contains("lacks internal parts")), "LangChain4jInternals check at startup");
    }

    /** Without langchain4j-agentic-patterns: no adapter bean, no goal path preview, nothing about patterns logged. */
    @Test
    void noPatternAdapterWithoutTheModule() throws Exception {
        // the "patterns" build (CI's second configuration) has the module: PatternAdaptersDevModeTest covers it
        assumeFalse(getClass().getClassLoader().getResource(
                "dev/langchain4j/agentic/patterns/goap/GoalOrientedPlanner.class") != null, "patterns module present");
        JsonNode beans = new DevUIBuildTimeDataTest("quarkus-arc") {
        }.getBuildTimeData("beans");
        List<String> classes = new ArrayList<>();
        beans.findValues("providerType").forEach(type -> classes.add(type.toString()));
        assertTrue(classes.stream().anyMatch(type -> type.contains("PatternAdapters")), () -> "registry bean in " + classes);
        assertTrue(classes.stream().noneMatch(type -> type.matches(".*(Goap|P2P|Blackboard)Adapter.*")), classes::toString);
        assertEquals(0, executeJsonRPCMethod("previewGoalPath", Map.of("systemId", ROOT, "inputKeys", List.of("topic")))
                .size(), "no goal path preview without the module");
        assertTrue(config.getLogRecords().stream().noneMatch(record -> record.getLoggerName().contains(".patterns")
                || String.valueOf(record.getThrown()).contains("NoClassDefFoundError")), "no pattern class loading issue");
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

    /**
     * The fixture run fails (the fixture app has no model provider, so the agent bean cannot be created): enough to see
     * the replay of the events and the end of the run over the Dev UI websocket. Streamed items arrive as
     * SubscriptionMessage results; the client learns the end from RunFinished / RunFailed.
     */
    @Test
    void streamRunReplaysTheRun() throws Exception {
        String runId = executeJsonRPCMethod("startRun", Map.of("systemId", ROOT, "inputs", Map.of("topic", "dragons")))
                .asText();

        List<JsonNode> events = rawJsonRpcStream("quarkus-langchain4j-agentic-viewer_streamRun",
                "{\"runId\":\"" + runId + "\"}",
                event -> List.of("RunFinished", "RunFailed").contains(event.path("type").asText()));

        assertEquals(List.of("RunStarted", "RunFailed"), events.stream().map(e -> e.path("type").asText()).toList());
        assertTrue(events.stream().allMatch(e -> runId.equals(e.path("runId").asText())));
        assertEquals(Map.of("topic", "dragons").toString(), toMap(events.get(0).path("inputs")).toString());
    }

    @Test
    void runHistory() throws Exception {
        String runId = executeJsonRPCMethod("startRun", Map.of("systemId", ROOT, "inputs", Map.of("topic", "history")))
                .asText();
        // the fixture run fails at once (no model provider in the fixture app): wait for its end in the history
        JsonNode summary = null;
        for (int i = 0; i < 50 && (summary == null || "RUNNING".equals(summary.path("status").asText())); i++) {
            Thread.sleep(100);
            for (JsonNode run : executeJsonRPCMethod("listRuns", Map.of("systemId", ROOT))) {
                if (runId.equals(run.path("runId").asText())) {
                    summary = run;
                }
            }
        }
        assertEquals("FAILED", summary.path("status").asText(), String.valueOf(summary));
        assertEquals("history", summary.path("inputs").path("topic").asText());

        JsonNode trace = executeJsonRPCMethod("getRun", Map.of("runId", runId));
        assertEquals(runId, trace.path("summary").path("runId").asText());
        List<String> types = new ArrayList<>();
        trace.path("events").forEach(event -> types.add(event.path("type").asText()));
        assertEquals(List.of("RunStarted", "RunFailed"), types);

        assertEquals(false, executeJsonRPCMethod("cancelRun", Map.of("runId", runId)).asBoolean(), "already ended");
        assertEquals(false, executeJsonRPCMethod("provideHumanInput", Map.of("runId", runId, "value", "yes")).asBoolean(),
                "nothing waits for human input");
        String unknown = rawJsonRpc("quarkus-langchain4j-agentic-viewer_getRun", "{\"runId\":\"nope\"}");
        assertTrue(unknown.contains("\"error\"") && unknown.contains("Unknown run: nope"), unknown);
        String unknownInput = rawJsonRpc("quarkus-langchain4j-agentic-viewer_provideHumanInput",
                "{\"runId\":\"nope\",\"value\":\"yes\"}");
        assertTrue(unknownInput.contains("\"error\"") && unknownInput.contains("Unknown run: nope"), unknownInput);
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

    private static Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(field -> map.put(field.getKey(), field.getValue().asText()));
        return map;
    }

    /**
     * Subscribes to a streaming JSON-RPC method over the Dev UI websocket and collects the streamed objects until one
     * matches {@code last}.
     */
    private List<JsonNode> rawJsonRpcStream(String method, String params, Predicate<JsonNode> last)
            throws Exception {
        int id = (int) (Math.random() * Integer.MAX_VALUE);
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> items = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(JSON_RPC_WS), new WebSocket.Listener() {
                    private final StringBuilder text = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean lastPart) {
                        text.append(data);
                        if (lastPart) {
                            try {
                                JsonNode message = mapper.readTree(text.toString());
                                JsonNode result = message.path("result");
                                if (message.path("id").asInt() == id
                                        && "SubscriptionMessage".equals(result.path("messageType").asText())) {
                                    items.add(result.path("object"));
                                    if (last.test(result.path("object"))) {
                                        done.complete(null);
                                    }
                                }
                            } catch (Exception e) {
                                done.completeExceptionally(e);
                            }
                            text.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).get(10, TimeUnit.SECONDS);
        try {
            socket.sendText("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params
                    + "}", true).get(10, TimeUnit.SECONDS);
            try {
                done.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("stream did not end, received: " + items, e);
            }
            return items;
        } finally {
            socket.abort();
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
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(JSON_RPC_WS),
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
