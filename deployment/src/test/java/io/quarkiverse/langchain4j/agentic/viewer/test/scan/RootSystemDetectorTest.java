package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentTree;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;

class RootSystemDetectorTest {

    private static AgentDescriptor agent(String agentClass, String... subAgents) {
        return new AgentDescriptor(agentClass + "#run", agentClass, "run",
                subAgents.length == 0 ? NodeKind.AI_AGENT : NodeKind.SEQUENCE, "run", null, null, null,
                List.of(subAgents), null, null, null, null, null, false, false, null, null, null);
    }

    /** Root and nesting as "A(B, C(D))"; an unresolved class is suffixed with "?". */
    private static String render(AgentTree tree) {
        String self = tree.agentClass() + (tree.descriptor() == null ? "?" : "");
        return tree.children().isEmpty() ? self
                : self + tree.children().stream().map(RootSystemDetectorTest::render).toList().toString()
                        .replace('[', '(').replace(']', ')');
    }

    private static List<String> detect(AgentDescriptor... descriptors) {
        return new RootSystemDetector().detect(List.of(descriptors)).stream().map(RootSystemDetectorTest::render).toList();
    }

    @Test
    void onlyRootsAreListedWithTheirSubAgentsNested() {
        assertEquals(List.of("Flow(Writer, Loop(Scorer, Editor))"),
                detect(agent("Scorer"), agent("Flow", "Writer", "Loop"), agent("Editor"), agent("Writer"),
                        agent("Loop", "Scorer", "Editor")));
    }

    @Test
    void sharedSubAgentAppearsInsideEveryRoot() {
        assertEquals(List.of("Planner(Writer)", "Story(Writer, Reviewer)"),
                detect(agent("Story", "Writer", "Reviewer"), agent("Planner", "Writer"), agent("Writer"),
                        agent("Reviewer")));
    }

    @Test
    void standaloneAgentIsASystem() {
        assertEquals(List.of("Cashier"), detect(agent("Cashier")));
    }

    @Test
    void subAgentWithoutAgentMethodIsAnUnresolvedLeaf() {
        List<AgentTree> roots = new RootSystemDetector().detect(List.of(agent("Flow", "Remote")));
        assertEquals("Flow(Remote?)", render(roots.get(0)));
        assertNull(roots.get(0).children().get(0).descriptor());
    }

    @Test
    void cycleIsCutAtTheRepeatedAncestor() {
        assertEquals(List.of("Root(A(B(A)))"), detect(agent("Root", "A"), agent("A", "B"), agent("B", "A")));
    }

    @Test
    void agentsInAPureCycleHaveNoRoot() {
        assertEquals(List.of(), detect(agent("A", "B"), agent("B", "A")));
    }
}
