package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerRecorder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunObservation;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;

/**
 * Builder systems reported by the transform: one that cannot be shown does not break the others, a system adopted by
 * another after it was listed disappears, and a new application starts without the previous one's systems.
 */
class BuilderSystemsRobustnessTest {

    private final BuilderSystems builderSystems = new BuilderSystems(new RunObservation() {
        @Override
        public void observe(String systemId, Object root) {
        }
    });

    @AfterEach
    void forget() {
        BuiltSystems.clear();
    }

    /** An agent whose shape can be read but whose topology cannot be built (a broken custom agent). */
    private static AgentInstance broken() {
        return (AgentInstance) Proxy.newProxyInstance(BuilderSystemsRobustnessTest.class.getClassLoader(),
                new Class<?>[] { AgentInstance.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "topology" -> AgenticSystemTopology.SEQUENCE;
                    case "name" -> "broken";
                    case "subagents" -> List.of();
                    case "parent", "type", "plannerType", "outputKey" -> null;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "broken";
                    default -> throw new IllegalStateException("broken agent: " + method.getName());
                });
    }

    private static UntypedAgent sequence(String name) {
        return AgenticServices.sequenceBuilder().name(name)
                .subAgents(AgenticServices.agentAction(scope -> scope.writeState(name, true))).build();
    }

    @Test
    void aSystemThatCannotBeShownIsSkipped() {
        BuiltSystems.register(broken(), "com.example.App#broken:1");
        BuiltSystems.register(sequence("fine"), "com.example.App#fine:2");

        List<String> first = builderSystems.built().stream().map(AgenticSystem::name).toList();
        List<String> second = builderSystems.built().stream().map(AgenticSystem::name).toList();

        assertEquals(List.of("fine"), first);
        assertEquals(List.of(), second, "nothing new, and the broken one is not tried again");
    }

    @Test
    void aSystemAdoptedAfterItWasListedIsStale() {
        UntypedAgent inner = sequence("inner");
        BuiltSystems.register(inner, "com.example.App#inner:1");
        assertEquals(List.of("inner"), builderSystems.built().stream().map(AgenticSystem::name).toList());

        AgenticServices.sequenceBuilder().name("outer").subAgents(inner).build(); // now a sub-agent

        assertEquals(List.of("com.example.App#inner:1"), builderSystems.stale());
        assertEquals(List.of(), builderSystems.stale(), "reported once");
    }

    @Test
    void aNewApplicationStartsWithoutThePreviousBuiltSystemsAndCandidates() {
        AgenticViewerRecorder recorder = new AgenticViewerRecorder();
        BuiltSystems.register(sequence("old"), "com.example.App#old:1");
        recorder.setBuilderCandidates(List.of(new BuilderCandidate("bean", "com.example.App", "old", null,
                UntypedAgent.class.getName())));

        recorder.setSystems(List.of()); // the static init of the next application

        assertTrue(BuiltSystems.latest().isEmpty());
        assertEquals(List.of(), AgenticViewerRecorder.getBuilderCandidates());
        assertEquals(Map.of(), BuiltSystems.latest());
    }
}
