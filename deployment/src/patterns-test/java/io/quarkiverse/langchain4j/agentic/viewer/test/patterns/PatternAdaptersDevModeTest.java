package io.quarkiverse.langchain4j.agentic.viewer.test.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIBuildTimeDataTest;
import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * With langchain4j-agentic-patterns on the class path (profile "patterns"), dev mode registers the three pattern
 * adapters and the topology tags the planners (mirror of AgenticSystemsDevModeTest.noPatternAdapterWithoutTheModule).
 */
public class PatternAdaptersDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(PatternSystems.class, PatternSystems.Writer.class,
                    PatternSystems.Goal.class, PatternSystems.Peers.class, PatternSystems.Board.class,
                    PatternSystems.EntryPoint.class, PatternSystems.FixtureChatModel.class));

    public PatternAdaptersDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void adaptersRegistered() throws Exception {
        JsonNode beans = new DevUIBuildTimeDataTest("quarkus-arc") {
        }.getBuildTimeData("beans");
        List<String> classes = new ArrayList<>();
        beans.findValues("providerType").forEach(type -> classes.add(type.toString()));
        for (String adapter : List.of("GoapAdapter", "P2PAdapter", "BlackboardAdapter")) {
            assertTrue(classes.stream().anyMatch(type -> type.contains(adapter)), () -> adapter + " in " + classes);
        }
    }

    @Test
    void topologyTagsThePlanners() throws Exception {
        JsonNode topology = executeJsonRPCMethod("getTopology",
                Map.of("systemId", PatternSystems.Goal.class.getName() + "#plan"));
        assertEquals("GOAP", topology.path("root").path("plannerInfo").path("patternType").asText(), topology::toString);
        JsonNode preview = executeJsonRPCMethod("previewGoalPath",
                Map.of("systemId", PatternSystems.Goal.class.getName() + "#plan", "inputKeys", List.of("topic")));
        assertEquals(List.of(PatternSystems.Goal.class.getName() + "#plan/Writer"),
                List.of(preview.path(PatternSystems.Goal.class.getName() + "#plan").get(0).asText()));
    }
}
