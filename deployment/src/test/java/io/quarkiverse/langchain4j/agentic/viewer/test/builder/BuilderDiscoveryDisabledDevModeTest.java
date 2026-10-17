package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * {@code builder-discovery=none}: no builder system is listed, and none of their beans is created.
 */
public class BuilderDiscoveryDisabledDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(PipelineSystems.class, PipelineSystems.Greeter.class,
                    PipelineSystems.FixtureChatModel.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.agentic.dev-ui.builder-discovery=none\n"),
                            "application.properties"))
            .setLogRecordPredicate(record -> true);

    public BuilderDiscoveryDisabledDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void noBuilderSystem() throws Exception {
        assertEquals(0, executeJsonRPCMethod("listSystems").size());
        assertEquals(0, config.getLogRecords().stream()
                .filter(record -> String.valueOf(record.getMessage()).startsWith("producing ")).count());
    }
}
