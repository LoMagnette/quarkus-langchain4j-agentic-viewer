package io.quarkiverse.langchain4j.agentic.viewer.test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIBuildTimeDataTest;
import io.quarkus.value.registry.ValueRegistry;

/**
 * Build-time data of another Dev UI namespace than the test's own. Since Quarkus 3.40 a DevUIBuildTimeDataTest finds the
 * application in its JUnit {@code @BeforeEach}, which JUnit never calls on an instance a test creates: the test passes
 * the ValueRegistry it is given as a parameter.
 */
public final class DevUiBuildTimeData {

    private DevUiBuildTimeData() {
    }

    public static JsonNode get(ValueRegistry valueRegistry, String namespace, String key) throws Exception {
        DevUIBuildTimeDataTest data = new DevUIBuildTimeDataTest(namespace) {
        };
        data.beforeEach(valueRegistry);
        return data.getBuildTimeData(key);
    }
}
