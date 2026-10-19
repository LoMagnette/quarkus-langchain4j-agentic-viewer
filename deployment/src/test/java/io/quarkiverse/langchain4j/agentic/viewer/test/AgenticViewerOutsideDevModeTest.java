package io.quarkiverse.langchain4j.agentic.viewer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticDescriptorsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticRootsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.AgenticSystemsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkus.builder.ConsumeFlag;
import io.quarkus.builder.ConsumeFlags;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Outside dev mode the extension registers neither its Dev UI card nor its JSON-RPC service, and does not even scan
 * the application for agentic systems.
 */
public class AgenticViewerOutsideDevModeTest {

    // The build step and the test method see different copies of static fields (different class loaders),
    // so the build step reports through a system property
    private static final String RESULT_PROPERTY = "agentic-viewer.test.dev-ui-items";
    // items no step produces outside dev mode: the build chain only accepts them as optional
    private static final ConsumeFlags OPTIONAL = ConsumeFlags.of(ConsumeFlag.OPTIONAL);

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(StoryWorkflow.class))
            .addBuildChainCustomizer(builder -> builder.addBuildStep(context -> {
                List<String> devUiItems = new ArrayList<>();
                context.consumeMulti(JsonRPCProvidersBuildItem.class).stream()
                        .filter(item -> item.getJsonRPCMethodProviderClass().getName()
                                .equals(AgenticViewerJsonRpcService.class.getName()))
                        .forEach(item -> devUiItems.add("JSON-RPC service"));
                context.consumeMulti(CardPageBuildItem.class).stream()
                        .filter(item -> item.getBuildTimeData().containsKey("agenticGraph"))
                        .forEach(item -> devUiItems.add("card"));
                if (context.consume(AgenticDescriptorsBuildItem.class) != null) {
                    devUiItems.add("descriptors");
                }
                if (context.consume(AgenticRootsBuildItem.class) != null) {
                    devUiItems.add("roots");
                }
                if (context.consume(AgenticSystemsBuildItem.class) != null) {
                    devUiItems.add("systems");
                }
                System.setProperty(RESULT_PROPERTY, devUiItems.toString());
                context.produce(new FeatureBuildItem("agentic-viewer-dev-ui-check"));
            })
                    .consumes(JsonRPCProvidersBuildItem.class)
                    .consumes(CardPageBuildItem.class)
                    .consumes(AgenticDescriptorsBuildItem.class, OPTIONAL)
                    .consumes(AgenticRootsBuildItem.class, OPTIONAL)
                    .consumes(AgenticSystemsBuildItem.class, OPTIONAL)
                    .produces(FeatureBuildItem.class)
                    .build());

    @Test
    void noDevUiRegistration() {
        assertEquals("[]", System.getProperty(RESULT_PROPERTY), "Dev UI items registered or systems scanned outside dev mode");
    }
}
