package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.MemoryOrder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderTopology;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Sub-agents of the same class under one parent (/X, /X~2) are told apart by identity, not by start order: in a
 * conditional workflow where only the second one runs, the second node is reported.
 */
@QuarkusTest
class RepeatedSiblingsTest {

    private static final String ID = "repeated";

    @Inject
    ChatModel chatModel;

    @Test
    void onlyTheSecondOfTwoSiblingsOfTheSameClassRuns() throws Exception {
        UntypedAgent root = AgenticServices.conditionalBuilder()
                .subAgents(scope -> false, AgenticServices.agentBuilder(MemoryOrder.ItemFinder.class).chatModel(chatModel)
                        .outputKey("first").build())
                .subAgents(scope -> true, AgenticServices.agentBuilder(MemoryOrder.ItemFinder.class).chatModel(chatModel)
                        .outputKey("second").build())
                .outputKey("second")
                .build();
        AgenticSystem system = BuilderTopology.system(ID, ID, new SourceRef(ID, ID, null), (AgentInstance) root);
        assertEquals(List.of(ID + "/ItemFinder", ID + "/ItemFinder~2"),
                system.root().children().stream().map(AgentNode::id).toList());
        RunManager runManager = new RunManager(id -> Optional.of(system), new CdiRunInvoker(id -> Optional.of(root)));
        try {
            assertTrue(RootListeners.attach(ID, root, new DevUiAgentListener(runManager)));

            Run run = runManager.run(runManager.startRun(ID, Map.of("request", "a lamp"))).orElseThrow();
            run.completion().get(30, TimeUnit.SECONDS);

            assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
            assertEquals(List.of(ID, ID + "/ItemFinder~2"), run.events().stream().filter(AgentStarted.class::isInstance)
                    .map(e -> ((AgentStarted) e).nodeId()).toList());
        } finally {
            runManager.shutdown();
        }
    }
}
