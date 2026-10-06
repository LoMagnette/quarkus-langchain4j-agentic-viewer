package io.quarkiverse.langchain4j.agentic.viewer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Outside dev mode the extension registers neither its Dev UI card nor its JSON-RPC service.
 */
public class AgenticViewerOutsideDevModeTest {

    // The build step and the test method see different copies of static fields (different class loaders),
    // so the build step reports through a system property
    private static final String RESULT_PROPERTY = "agentic-viewer.test.dev-ui-items";

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
                System.setProperty(RESULT_PROPERTY, devUiItems.toString());
                context.produce(new FeatureBuildItem("agentic-viewer-dev-ui-check"));
            })
                    .consumes(JsonRPCProvidersBuildItem.class)
                    .consumes(CardPageBuildItem.class)
                    .produces(FeatureBuildItem.class)
                    .build());

    @Test
    void noDevUiRegistration() {
        assertEquals("[]", System.getProperty(RESULT_PROPERTY), "Dev UI items registered outside dev mode");
    }
}
