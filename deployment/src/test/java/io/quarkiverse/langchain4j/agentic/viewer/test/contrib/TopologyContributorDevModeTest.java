package io.quarkiverse.langchain4j.agentic.viewer.test.contrib;

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
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * In dev mode, the application's AgenticTopologyContributors (found at build time) describe their planner nodes,
 * served by getContributions with resolved node ids.
 */
public class TopologyContributorDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(ReviewSystem.class, ReviewSystem.Writer.class,
                    ReviewSystem.Reviewer.class, ReviewSystem.ReviewTopology.class, ReviewSystem.FailingTopology.class,
                    ReviewSystem.OtherTopology.class, ReviewSystem.EntryPoint.class, ReviewSystem.FixtureChatModel.class));

    private static final String ROOT = ReviewSystem.class.getName() + "#review";

    public TopologyContributorDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void contributionsOfThePlannerNode() throws Exception {
        JsonNode contributions = executeJsonRPCMethod("getContributions", Map.of("systemId", ROOT));
        assertEquals(List.of(ROOT), List.copyOf(fieldNames(contributions)), "only the described planner node");
        JsonNode contribution = contributions.path(ROOT);
        assertEquals(1, contribution.path("nodes").size());
        assertEquals(ROOT + "::done", contribution.path("nodes").get(0).path("id").asText());
        assertEquals("approved", contribution.path("nodes").get(0).path("label").asText());
        List<String> edges = new ArrayList<>();
        contribution.path("edges").forEach(edge -> edges.add(edge.path("source").asText().replace(ROOT, "") + " -> "
                + edge.path("target").asText().replace(ROOT, "") + " " + (edge.path("label").isTextual() ? edge.path("label").asText() : "-")));
        assertEquals(List.of("/Writer -> /Reviewer text", "/Reviewer -> /Writer rejected", "/Reviewer -> ::done -"),
                edges, "references resolved, the edge to an unknown end dropped, the failing contributor ignored");
    }

    @Test
    void unknownSystemIsAnError() throws Exception {
        String answer = rawJsonRpc("quarkus-langchain4j-agentic-viewer_getContributions", "{\"systemId\":\"com.example.Nope#run\"}");
        assertTrue(answer.contains("\"error\"") && answer.contains("Unknown agentic system"), answer);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** One JSON-RPC call over a raw websocket: DevUIJsonRPCTest cannot read error answers. */
    private String rawJsonRpc(String method, String params) throws Exception {
        int id = 4242;
        CompletableFuture<String> answer = new CompletableFuture<>();
        StringBuilder text = new StringBuilder();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(uri.toString().replaceFirst("^http", "ws")),
                new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        text.append(data);
                        if (last) {
                            String message = text.toString();
                            text.setLength(0);
                            if (message.matches("(?s).*\"id\"\\s*:\\s*\"?" + id + "\\b.*")) {
                                answer.complete(message);
                            }
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).get(10, TimeUnit.SECONDS);
        socket.sendText("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params + "}", true);
        try {
            return answer.get(10, TimeUnit.SECONDS);
        } finally {
            socket.abort();
        }
    }
}
