package io.quarkiverse.langchain4j.agentic.viewer.test.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.agentic.viewer.test.StoryWorkflow;
import io.quarkus.devui.tests.DevUIBuildTimeDataTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * quarkus.langchain4j.agentic.dev-ui.enabled=false: no card, no JSON-RPC service, no listener or planner decorators
 * (their beans are not registered), even in dev mode.
 */
public class DisabledDevUiDevModeTest extends DevUIBuildTimeDataTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(StoryWorkflow.class)
                    .addAsResource(new org.jboss.shrinkwrap.api.asset.StringAsset(
                            "quarkus.langchain4j.agentic.dev-ui.enabled=false\n"), "application.properties"));

    public DisabledDevUiDevModeTest() {
        super("devui");
    }

    @Test
    void noCard() throws Exception {
        JsonNode extensions = getBuildTimeData("extensions");
        List<String> titles = new ArrayList<>();
        extensions.findValues("title").forEach(title -> titles.add(title.asText()));
        assertTrue(titles.stream().noneMatch(title -> title.equals("Agentic Systems")), titles::toString);
    }

    @Test
    void noBeans() throws Exception {
        JsonNode beans = new DevUIBuildTimeDataTest("quarkus-arc") {
        }.getBuildTimeData("beans");
        List<String> classes = new ArrayList<>();
        beans.findValues("providerType").forEach(type -> classes.add(type.toString()));
        assertTrue(classes.stream().anyMatch(type -> type.contains("StoryWorkflow")), "the app itself is there: " + classes);
        for (String bean : List.of("AgenticViewerJsonRpcService", "AgenticSystemRegistry", "RunManager", "RunObservation",
                "PatternAdapters")) {
            assertTrue(classes.stream().noneMatch(type -> type.contains(bean)), bean + " registered: " + classes);
        }
    }
}
