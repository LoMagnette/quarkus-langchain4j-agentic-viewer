package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * T5.4, builder-discovery=transform: the systems the application builds are listed too, beans or not, once built; a
 * system built several times at one call site is listed once; the CDI ones stay.
 */
public class BuilderTransformDevModeTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClasses(FieldSystems.class, PipelineSystems.class,
                    PipelineSystems.Greeter.class, PipelineSystems.FixtureChatModel.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.agentic.dev-ui.builder-discovery=transform\n"),
                            "application.properties"));

    private static final String FIELD_SYSTEMS = FieldSystems.class.getName();

    public BuilderTransformDevModeTest() {
        super("quarkus-langchain4j-agentic-viewer");
    }

    @Test
    void builtSystemsListed() throws Exception {
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode system : executeJsonRPCMethod("listSystems")) {
            names.add(system.get("name").asText() + " " + system.get("origin").asText());
            ids.add(system.get("id").asText());
        }
        // CDI producers once (not again through their build() calls), the field system, the per-call one once (T5.5a),
        // the call site building two shapes twice, no nested system (inner, cleanup)
        assertEquals(List.of("Greeter BUILDER", "archiver BUILDER", "perCall BUILDER", "pipeline BUILDER",
                "variable BUILDER", "variable BUILDER"), names.stream().sorted().toList(), ids::toString);
        String variable = ids.stream().filter(id -> id.startsWith(FIELD_SYSTEMS + "#variable:")).sorted().findFirst()
                .orElseThrow();
        assertEquals(List.of(variable, variable + "~2"), ids.stream().filter(id -> id.startsWith(variable)).sorted().toList());

        String perCall = ids.stream().filter(id -> id.startsWith(FIELD_SYSTEMS + "#perCall:")).findFirst().orElseThrow();
        JsonNode topology = executeJsonRPCMethod("getTopology", Map.of("systemId", perCall));
        assertEquals("LOOP", topology.get("root").get("children").get(0).get("kind").asText());
        assertEquals(perCall, topology.get("sourceRef").get("callSite").asText());
        assertEquals("perCall", topology.get("sourceRef").get("methodName").asText());

        // and they run
        String runId = executeJsonRPCMethod("startRun", Map.of("systemId", perCall, "inputs", Map.of())).asText();
        JsonNode summary = null;
        for (int i = 0; i < 100 && (summary == null || "RUNNING".equals(summary.path("status").asText())); i++) {
            Thread.sleep(100);
            for (JsonNode run : executeJsonRPCMethod("listRuns", Map.of("systemId", perCall))) {
                if (runId.equals(run.path("runId").asText())) {
                    summary = run;
                }
            }
        }
        assertEquals("SUCCEEDED", summary.path("status").asText());
    }
}
