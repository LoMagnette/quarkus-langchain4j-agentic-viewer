package io.quarkiverse.langchain4j.agentic.viewer.test.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.CodeRef;
import io.quarkiverse.langchain4j.agentic.viewer.test.scan.ScanFixtures;

/**
 * Each node carries the application code behind it, as langchain4j finds it, for the Dev UI to open in the IDE.
 */
class CodeRefsTest {

    private static final String P = ScanFixtures.class.getName() + "$";
    private static Map<String, AgenticSystem> systems;

    @BeforeAll
    static void build() throws Exception {
        systems = new TopologyBuilder().build(new RootSystemDetector().detect(new AgenticAnnotationScanner()
                .scan(Index.of(ScanFixtures.class.getDeclaredClasses())))).stream()
                .collect(Collectors.toMap(AgenticSystem::id, Function.identity()));
    }

    private static List<String> code(String systemId) {
        return systems.get(systemId).root().code().stream()
                .map(ref -> ref.role() + " " + ref.className().substring(P.length())
                        + (ref.member() != null ? "#" + ref.member() : "") + (ref.resource() != null ? " " + ref.resource() : ""))
                .toList();
    }

    @Test
    void loopWithItsOutputAndExitCondition() {
        assertEquals(List.of("AGENT Polisher#polish", "OUTPUT Polisher#finalDraft", "EXIT_CONDITION Polisher#goodEnough"),
                code(P + "Polisher#polish"));
    }

    @Test
    void conditionalWithItsActivationConditions() {
        assertEquals(List.of("AGENT Router#route", "ACTIVATION_CONDITION Router#technical",
                "ACTIVATION_CONDITION Router#general"), code(P + "Router#route"));
    }

    @Test
    void plannerWithItsSupplier() {
        assertEquals(List.of("AGENT DraftPlanner#plan", "PLANNER_SUPPLIER DraftPlanner#planner"),
                code(P + "DraftPlanner#plan"));
    }

    @Test
    void aiAgentWithItsPromptResource() {
        assertEquals(List.of("AGENT Cashier#pay", "SYSTEM_MESSAGE Cashier prompts/cashier-system.txt"),
                code(P + "Cashier#pay"));
        assertEquals(CodeRef.Role.AGENT, systems.get(P + "StoryFlow#write").root().children().get(0).code().get(0).role(),
                "sub-agents carry their code too");
    }
}
