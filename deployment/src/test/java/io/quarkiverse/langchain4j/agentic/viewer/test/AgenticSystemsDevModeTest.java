package io.quarkiverse.langchain4j.agentic.viewer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIBuildTimeDataTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * In dev mode, the declarative systems built at build time reach the Dev UI as build-time data and runtime-dev
 * through the recorder, with the same node ids.
 */
public class AgenticSystemsDevModeTest extends DevUIBuildTimeDataTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(StoryWorkflow.class, RecordedSystemsRoute.class));

    private static final String ROOT = StoryWorkflow.class.getName() + "#writeStory";

    public AgenticSystemsDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void systemsAreBuildTimeData() throws Exception {
        JsonNode systems = getBuildTimeData("agenticSystems");

        assertEquals(1, systems.size(), systems::toString);
        JsonNode system = systems.get(0);
        assertEquals(ROOT, system.get("id").asText());
        assertEquals("StoryWorkflow", system.get("name").asText());
        assertEquals("DECLARATIVE", system.get("origin").asText());
        List<String> childIds = new ArrayList<>();
        system.get("root").get("children").forEach(child -> childIds.add(child.get("id").asText()));
        assertEquals(List.of(ROOT + "/Writer", ROOT + "/Editor"), childIds);
    }

    @Test
    void systemsAreRecordedForRuntimeDev() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri.resolve("/recorded-systems")).build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(ROOT + " [/Writer, /Editor]", response.body());
    }
}
