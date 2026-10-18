package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderTopology;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * T5.3: a builder system observed once found (as BuilderSystems does) reports its runs like a declarative one: agent
 * events on the nodes of its topology, loop iterations, and the decisions of a plannerBuilder planner.
 */
@QuarkusTest
class BuilderRunEventsTest {

    private static final String CREATOR = "creator";
    private static final String REVIEWER = "reviewer";

    private static RunManager runManager;
    private static Map<String, AgenticSystem> systems;

    @Inject
    @Named(BuilderStoryCreator.NAME)
    UntypedAgent builderStoryCreator;

    @Inject
    @Named(BuilderDraftReviewer.NAME)
    UntypedAgent builderDraftReviewer;

    /** Once: a listener cannot be removed from the application-scoped roots. */
    @BeforeEach
    void observe() {
        if (runManager != null) {
            return;
        }
        Map<String, Object> roots = Map.of(CREATOR, ClientProxy.unwrap(builderStoryCreator), REVIEWER,
                ClientProxy.unwrap(builderDraftReviewer));
        systems = Map.of(
                CREATOR, BuilderTopology.system(CREATOR, CREATOR, new SourceRef(CREATOR, CREATOR, null),
                        (AgentInstance) roots.get(CREATOR)),
                REVIEWER, BuilderTopology.system(REVIEWER, REVIEWER, new SourceRef(REVIEWER, REVIEWER, null),
                        (AgentInstance) roots.get(REVIEWER)));
        runManager = new RunManager(id -> Optional.ofNullable(systems.get(id)),
                new CdiRunInvoker(id -> Optional.ofNullable(roots.get(id))));
        DevUiAgentListener listener = new DevUiAgentListener(runManager);
        roots.forEach((id, root) -> assertTrue(RootListeners.attach(id, root, listener), id));
    }

    @AfterAll
    static void shutDown() {
        if (runManager != null) {
            runManager.shutdown();
        }
    }

    private static Run run(String systemId, Map<String, Object> inputs) throws Exception {
        Run run = runManager.run(runManager.startRun(systemId, inputs)).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> systemId + ": " + run.events());
        return run;
    }

    private static List<String> nodeIds(AgentNode node) {
        List<String> ids = new ArrayList<>(List.of(node.id()));
        node.children().forEach(child -> ids.addAll(nodeIds(child)));
        return ids;
    }

    @Test
    void agentEventsOnTheBuilderTopology() throws Exception {
        Run run = run(CREATOR, Map.of("topic", "a lighthouse"));

        List<AgentStarted> started = run.events().stream().filter(AgentStarted.class::isInstance)
                .map(AgentStarted.class::cast).toList();
        assertTrue(nodeIds(systems.get(CREATOR).root()).containsAll(started.stream().map(AgentStarted::nodeId).toList()),
                () -> "every event on a node: " + started);
        assertEquals(List.of("creator", "creator/Drafter", "creator/UntypedAgent", "creator/UntypedAgent/StyleScorer",
                "creator/UntypedAgent/StyleEditor", "creator/UntypedAgent/StyleScorer", "creator/UntypedAgent/StyleEditor",
                "creator/UntypedAgent/StyleScorer", "creator/UntypedAgent/StyleEditor"),
                started.stream().map(AgentStarted::nodeId).toList());
        assertEquals(List.of(0, 0, 1, 1, 2, 2), started.stream().filter(e -> e.nodeId().contains("/Style"))
                .map(AgentStarted::iteration).toList(), "loop iterations, from 0 as for declarative loops");
    }

    @Test
    void plannerBuilderDecisions() throws Exception {
        assertEquals(List.of("topic"), systems.get(REVIEWER).inputs().stream().map(input -> input.name()).toList(),
                "the form of the declarative DraftReviewer");
        Run run = run(REVIEWER, Map.of("topic", "solar panels"));

        assertEquals("A polished draft about solar panels", run.output());
        assertEquals(List.of("call: [Drafter]", "call: [Reviewer]", "call: [Drafter]", "call: [Reviewer]",
                "done (done): []"), run.events().stream().filter(PlannerDecision.class::isInstance)
                        .map(PlannerDecision.class::cast)
                        .map(d -> d.extras().get("action") + (d.done() ? " (done)" : "") + ": "
                                + d.selectedNodeIds().stream().map(id -> id.substring(id.lastIndexOf('/') + 1)).toList())
                        .toList());
    }
}
