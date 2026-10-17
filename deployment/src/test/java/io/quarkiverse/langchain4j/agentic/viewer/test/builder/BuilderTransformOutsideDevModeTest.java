package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * builder-discovery=transform never rewrites classes outside dev mode (here: test mode), so nothing in a production
 * build refers to the dev-only registry.
 */
public class BuilderTransformOutsideDevModeTest {

    // the build step and the test method see different copies of static fields: report through a system property
    private static final String RESULT_PROPERTY = "agentic-viewer.test.transformed-classes";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(FieldSystems.class, PipelineSystems.FixtureChatModel.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.agentic.dev-ui.builder-discovery=transform\n"),
                            "application.properties"))
            .addBuildChainCustomizer(builder -> builder.addBuildStep(context -> {
                List<String> transformed = new ArrayList<>();
                context.consumeMulti(BytecodeTransformerBuildItem.class).stream()
                        .map(BytecodeTransformerBuildItem::getClassToTransform)
                        .filter(name -> name.equals(FieldSystems.class.getName()))
                        .forEach(transformed::add);
                System.setProperty(RESULT_PROPERTY, transformed.toString());
                context.produce(new FeatureBuildItem("agentic-viewer-transform-check"));
            })
                    .consumes(BytecodeTransformerBuildItem.class)
                    .produces(FeatureBuildItem.class)
                    .build());

    @Test
    void noTransformOutsideDevMode() {
        assertEquals("[]", System.getProperty(RESULT_PROPERTY));
    }
}
