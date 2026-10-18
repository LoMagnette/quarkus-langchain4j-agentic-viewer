package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * {@code builder-discovery=none}: no builder system is listed, and none of their beans is created; a declarative system
 * whose agent injects a builder system stays DECLARATIVE.
 */
public class BuilderDiscoveryDisabledDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(PipelineSystems.class, PipelineSystems.Greeter.class,
                    PipelineSystems.FixtureChatModel.class, DelegatingWorkflow.class, DelegatingWorkflow.Step.class,
                    DelegatingWorkflow.EntryPoint.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.agentic.dev-ui.builder-discovery=none\n"),
                            "application.properties"))
            .setLogRecordPredicate(record -> true);

    public BuilderDiscoveryDisabledDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void noBuilderSystem() throws Exception {
        JsonNode systems = executeJsonRPCMethod("listSystems");
        assertEquals(1, systems.size(), systems::toString);
        assertEquals(DelegatingWorkflow.class.getName() + "#run", systems.get(0).get("id").asText());
        assertEquals("DECLARATIVE", systems.get(0).get("origin").asText());
        assertEquals(0, config.getLogRecords().stream()
                .filter(record -> String.valueOf(record.getMessage()).startsWith("producing ")).count());
    }
}
