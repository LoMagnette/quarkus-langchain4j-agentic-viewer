package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.agentic.deployment.DetectedAiAgentAsMapBuildItem;
import io.quarkiverse.langchain4j.agentic.deployment.DetectedAiAgentBuildItem;
import io.quarkiverse.langchain4j.agentic.deployment.DetectedNonAiAgentAsMapBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.UpstreamAgents;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * In a real build, quarkus-langchain4j-agentic's detection and the scanner's own rules agree on the agents of the scan
 * fixtures (every workflow kind, AI and non-AI agents, human-in-the-loop, tools), and the scan with the detection gives
 * the same descriptors as the scanner alone (model names, tools, skills included). The viewer's scan step only runs in dev mode: the comparison runs in a test
 * step. (No registry agent: quarkus-langchain4j-agentic 1.14.2 rejects a @RegistryAgent sub-agent at build time.)
 */
public class UpstreamAgreementTest {

    // the build step and the test method run in different class loaders: report through system properties
    private static final String DISAGREEMENTS = "agentic-viewer.test.upstream-disagreements";
    private static final String SAME_AGENTS = "agentic-viewer.test.upstream-same-agents";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(ScanFixtures.class)
                    .addAsResource("prompts/cashier-system.txt"))
            .addBuildChainCustomizer(builder -> builder.addBuildStep(context -> {
                var index = context.consume(CombinedIndexBuildItem.class).getIndex();
                AgenticAnnotationScanner scanner = new AgenticAnnotationScanner();
                AgenticAnnotationScanner.Result result = scanner.scan(index, UpstreamAgents.of(
                        context.consume(DetectedAiAgentAsMapBuildItem.class),
                        context.consume(DetectedNonAiAgentAsMapBuildItem.class),
                        context.consumeMulti(DetectedAiAgentBuildItem.class)));
                System.setProperty(DISAGREEMENTS, String.join("\n", result.disagreements()));
                // the same agents, and the same AI details whether resolved upstream or read by the scanner
                System.setProperty(SAME_AGENTS, String.valueOf(result.descriptors().equals(scanner.scan(index))));
                context.produce(new FeatureBuildItem("agentic-viewer-upstream-check"));
            }).consumes(CombinedIndexBuildItem.class).consumes(DetectedAiAgentAsMapBuildItem.class)
                    .consumes(DetectedNonAiAgentAsMapBuildItem.class).consumes(DetectedAiAgentBuildItem.class)
                    .produces(FeatureBuildItem.class).build());

    @Test
    void theyAgree() {
        assertEquals("", System.getProperty(DISAGREEMENTS));
        assertEquals("true", System.getProperty(SAME_AGENTS));
    }
}
