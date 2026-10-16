package io.quarkiverse.langchain4j.agentic.viewer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * In dev mode, the JSON-RPC service from runtime-dev answers over the Dev UI websocket, with the systems recorded at
 * build time.
 */
public class AgenticViewerJsonRpcDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(StoryWorkflow.class));

    public AgenticViewerJsonRpcDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void systemCountIsServedFromRuntimeDev() throws Exception {
        JsonNode systemCount = executeJsonRPCMethod("getSystemCount");

        assertEquals(1, systemCount.asInt(), "the StoryWorkflow sequence is the only root system");
    }
}
