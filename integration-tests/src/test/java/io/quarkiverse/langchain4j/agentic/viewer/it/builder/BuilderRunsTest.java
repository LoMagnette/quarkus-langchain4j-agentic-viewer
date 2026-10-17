package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticSystemRegistry;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderTopology;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

/**
 * T5.6: the builder samples run as the Dev UI starts them: an UntypedAgent with a map of the form values, a typed
 * builder interface through its method; their forms come from the inputs of their agents or of the interface method.
 * The registry and run manager are built here (dev-mode beans), with the systems BuilderSystems would find.
 */
@QuarkusTest
class BuilderRunsTest {

    @Inject
    @Named(BuilderStoryCreator.NAME)
    UntypedAgent builderStoryCreator;

    @Inject
    BuilderStoryWriter builderStoryWriter;

    private static final String CREATOR = "creator";
    private static final String WRITER = "writer";

    private RunManager runManager;

    private AgenticViewerJsonRpcService service() {
        Map<String, Object> roots = Map.of(CREATOR, ClientProxy.unwrap(builderStoryCreator), WRITER,
                ClientProxy.unwrap(builderStoryWriter));
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        roots.forEach((id, root) -> registry.register(BuilderTopology.system(id, id, new SourceRef(id, id, null),
                (AgentInstance) root)));
        runManager = new RunManager(registry::find, new CdiRunInvoker(id -> Optional.ofNullable(roots.get(id))));
        return new AgenticViewerJsonRpcService(registry, runManager);
    }

    /** Waits for the end of a run and returns its output. */
    private Object output(String runId) throws Exception {
        Run run = runManager.run(runId).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> runId + ": " + run.events());
        return run.output();
    }

    @Test
    void untypedAgentRunsWithAMap() throws Exception {
        AgenticViewerJsonRpcService service = service();

        assertEquals(Map.of("type", "object", "properties", Map.of("topic", Map.of("type", "string")), "required",
                List.of("topic")), service.getInputSchema(CREATOR));
        assertEquals("A story about a lighthouse (draft 4)", output(service.startRun(CREATOR, Map.of("topic", "a lighthouse"))));
    }

    @Test
    void typedBuilderInterfaceRunsThroughItsMethod() throws Exception {
        AgenticViewerJsonRpcService service = service();

        assertEquals(Map.of("type", "object", "properties", Map.of("bio", Map.of("type", "string")), "required",
                List.of("bio")), service.getInputSchema(WRITER));
        assertEquals("Once upon a time, there was Ada, a curious engineer.\n\n"
                + "One day, a dragon knocked at the door and asked for help.",
                output(service.startRun(WRITER, Map.of("bio", "Ada, a curious engineer"))));
    }
}
