package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ErrorHandled;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.RunFailed;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.DevUiAgentListener;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RootListeners;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.Run;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunStatus;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Error handling is reported: a retry by an @ErrorHandler, and a fatal error, with the failing agent and its exception.
 */
@QuarkusTest
class ErrorHandlingEventsTest {

    private static List<AgenticSystem> systems;
    private static RunManager runManager;

    @BeforeAll
    static void observe() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        systems = new ArrayList<>(new TopologyBuilder().build(new RootSystemDetector()
                .detect(new AgenticAnnotationScanner().scan(Index.of(samples)))));
        systems.addAll(new TopologyBuilder().build(new RootSystemDetector().detect(new AgenticAnnotationScanner()
                .scan(Index.of(FailingFlow.class, FailingFlow.Exploder.class)))));
        runManager = new RunManager(id -> systems.stream().filter(s -> s.id().equals(id)).findFirst(),
                new CdiRunInvoker());
        RootListeners.attach(systems, new DevUiAgentListener(runManager));
    }

    @AfterAll
    static void shutDown() {
        runManager.shutdown();
    }

    private static Run run(String name, Map<String, Object> inputs) throws Exception {
        AgenticSystem system = systems.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
        Run run = runManager.run(runManager.startRun(system.id(), inputs)).orElseThrow();
        run.completion().get(30, TimeUnit.SECONDS);
        return run;
    }

    private static List<ErrorHandled> handled(Run run) {
        return run.events().stream().filter(ErrorHandled.class::isInstance).map(ErrorHandled.class::cast).toList();
    }

    @Test
    void retryByTheErrorHandlerIsReported() throws Exception {
        Run run = run("ResilientStoryWriter", Map.of());

        assertEquals(RunStatus.SUCCEEDED, run.status(), () -> run.events().toString());
        assertEquals("A tiny tale of dragons and wizards.", run.output());
        List<ErrorHandled> handled = handled(run);
        assertEquals(1, handled.size(), handled::toString);
        ErrorHandled retry = handled.get(0);
        String root = systems.stream().filter(s -> s.name().equals("ResilientStoryWriter")).findFirst().orElseThrow().id();
        assertEquals(root + "/TopicWriter", retry.nodeId(), "the agent that failed");
        assertEquals("writeShortStory", retry.agentName());
        assertEquals("dev.langchain4j.agentic.agent.MissingArgumentException", retry.error().exceptionClass());
        assertEquals("RETRY", retry.outcome());
        List<String> types = run.events().stream().map(ExecutionEvent::getType).toList();
        assertTrue(types.indexOf("ErrorHandled") < types.lastIndexOf("AgentFinished"), "then the retry succeeds: " + types);
    }

    @Test
    void fatalErrorNamesTheFailingAgent() throws Exception {
        Run run = run("FailingFlow", Map.of("text", "x"));

        assertEquals(RunStatus.FAILED, run.status());
        ErrorHandled fatal = handled(run).get(0);
        String root = systems.stream().filter(s -> s.name().equals("FailingFlow")).findFirst().orElseThrow().id();
        assertEquals(root + "/Exploder", fatal.nodeId());
        assertEquals("THROW_EXCEPTION", fatal.outcome());
        assertTrue(fatal.error().stackTrace().contains("boom on x"), fatal.error()::toString);
        RunFailed failed = (RunFailed) run.events().get(run.events().size() - 1);
        assertTrue(failed.error().stackTrace().contains("boom on x"));
        assertTrue(run.events().stream().anyMatch(e -> e.getType().equals("AgentFailed")), "the agent failure itself");
    }
}
