package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.CodeRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;

/**
 * A builder system carries where it is built (the call site the transform reported, with its line, or the producer)
 * and the application class of its typed agents.
 */
class BuilderCodeRefsTest {

    /** A typed agent interface given to a builder: no agentic annotation. */
    public interface Greeter {
        String greet(@V("name") String name);
    }

    @Test
    void callSiteAndTypedAgent() {
        Greeter greeter = AgenticServices.sequenceBuilder(Greeter.class)
                .subAgents(AgenticServices.agentAction(scope -> scope.writeState("greeting", "hello"))).build();

        AgentNode root = BuilderTopology.system("com.example.App#build:42", "greet",
                new SourceRef("com.example.App", "build", "com.example.App#build:42"), (AgentInstance) greeter).root();

        assertEquals(List.of(new CodeRef(CodeRef.Role.CALL_SITE, "com.example.App", "build", null, 42),
                CodeRef.method(CodeRef.Role.AGENT, Greeter.class.getName(), "greet")), root.code());
        assertEquals(List.of(), root.children().get(0).code(), "an agentAction is the library's, no application code");
    }

    @Test
    void aProducedSystemPointsAtItsProducer() {
        AgentNode root = BuilderTopology.system("com.example.Producers#pipeline", "pipeline",
                new SourceRef("com.example.Producers", "pipeline", null),
                (AgentInstance) AgenticServices.sequenceBuilder()
                        .subAgents(AgenticServices.agentAction(scope -> scope.writeState("x", 1))).build()).root();

        assertEquals(List.of(new CodeRef(CodeRef.Role.CALL_SITE, "com.example.Producers", "pipeline", null, null)),
                root.code());
    }
}
