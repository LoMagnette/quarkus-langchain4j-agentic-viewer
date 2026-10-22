package io.quarkiverse.langchain4j.agentic.viewer.test.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.agentic.viewer.test.StoryWorkflow;
import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * quarkus.langchain4j.agentic.dev-ui.application-runs=false: the runs the application starts are not recorded, and the
 * Dev UI switch cannot turn them on.
 */
public class ApplicationRunsOffDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(StoryWorkflow.class).addAsResource(new StringAsset(
                    "quarkus.langchain4j.agentic.dev-ui.application-runs=false\n"), "application.properties"));

    private static final String ROOT = StoryWorkflow.class.getName() + "#writeStory";

    public ApplicationRunsOffDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void applicationRunsAreOff() throws Exception {
        assertFalse(executeJsonRPCMethod("getApplicationRuns", Map.of("systemId", ROOT)).path("enabled").asBoolean());
        JsonNode state = executeJsonRPCMethod("setRecordApplicationRuns", Map.of("systemId", ROOT, "record", true));
        assertFalse(state.path("enabled").asBoolean(), "the switch does not turn them on");
    }
}
