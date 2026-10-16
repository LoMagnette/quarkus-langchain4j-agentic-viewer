package io.quarkiverse.langchain4j.agentic.viewer.test.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * A hot reload rebuilds the registry from the changed code, ends the open runs and leaves no thread behind (T6.2).
 */
public class HotReloadDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(WaitingWorkflow.class, WaitingWorkflow.Ask.class,
                    WaitingWorkflow.EntryPoint.class, WaitingWorkflow.FixtureChatModel.class));

    private static final String ROOT = WaitingWorkflow.class.getName() + "#run";

    public HotReloadDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    /** Dev mode applies changed sources on the next HTTP request. */
    private static void reload() throws Exception {
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:8080/q/dev-ui/")).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static long viewerThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().startsWith("agentic-viewer-")).count();
    }

    private String stepDescription() throws Exception {
        JsonNode topology = executeJsonRPCMethod("getTopology", Map.of("systemId", ROOT));
        return topology.path("root").path("children").get(0).path("description").asText();
    }

    /** Starts a run, which stays open on its human step. */
    private String openRun() throws Exception {
        String runId = executeJsonRPCMethod("startRun", Map.of("systemId", ROOT, "inputs", Map.of("topic", "t"))).asText();
        for (int i = 0; i < 50; i++) {
            JsonNode trace = executeJsonRPCMethod("getRun", Map.of("runId", runId));
            for (JsonNode event : trace.path("events")) {
                if ("AwaitingHumanInput".equals(event.path("type").asText())) {
                    return runId;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("the run does not wait on its human step");
    }

    @Test
    void reloadsRebuildTheRegistryAndLeaveNoThreads() throws Exception {
        assertEquals("Approve the topic?", stepDescription());
        openRun();
        config.modifySourceFile(WaitingWorkflow.class, source -> source.replace("Approve the topic?", "Approve this topic?"));
        reload();
        assertEquals("Approve this topic?", stepDescription(), "registry rebuilt from the changed agent interface");
        assertEquals(0, executeJsonRPCMethod("listRuns", Map.of("systemId", ROOT)).size(), "runs of the old application gone");

        for (int i = 0; i < 10; i++) {
            openRun();
            int round = i;
            config.modifySourceFile(WaitingWorkflow.class, source -> source.replaceAll(
                    "Approve this topic\\?( \\(round \\d+\\))?", "Approve this topic? (round " + round + ")"));
            reload();
        }
        assertEquals("Approve this topic? (round 9)", stepDescription());
        for (int i = 0; i < 50 && viewerThreads() > 2; i++) {
            Thread.sleep(100);
        }
        assertTrue(viewerThreads() <= 2, "after 10 reloads with open runs, only the current application's threads: "
                + Thread.getAllStackTraces().keySet().stream().map(Thread::getName).filter(n -> n.startsWith("agentic-viewer-")).toList());
    }
}
