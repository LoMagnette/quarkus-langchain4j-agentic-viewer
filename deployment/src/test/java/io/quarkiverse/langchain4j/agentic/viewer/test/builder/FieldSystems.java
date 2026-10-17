package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import io.quarkus.runtime.StartupEvent;

/**
 * Fixture app: builder systems that are no beans, a system kept in a field, one built on every call and one call site
 * building two shapes, all used at startup. Only builder-discovery=transform finds them.
 */
@ApplicationScoped
public class FieldSystems {

    private UntypedAgent archiver;

    void start(@Observes StartupEvent startup) {
        archiver = AgenticServices.sequenceBuilder().name("archiver")
                .subAgents(AgenticServices.agentAction(scope -> scope.writeState("archived", true))).build();
        archiver.invoke(Map.of());
        for (int request = 0; request < 3; request++) {
            perCall().invoke(Map.of("request", request));
        }
        // one call site, two shapes
        variable(false).invoke(Map.of());
        variable(true).invoke(Map.of());
        variable(false).invoke(Map.of());
    }

    /** One build() call building systems of two shapes, depending on its argument. */
    UntypedAgent variable(boolean twoSteps) {
        List<Object> steps = new ArrayList<>(List.of(AgenticServices.agentAction(scope -> scope.writeState("step", 1))));
        if (twoSteps) {
            steps.add(AgenticServices.agentAction(scope -> scope.writeState("step", 2)));
        }
        return AgenticServices.sequenceBuilder().name("variable").subAgents(steps).build();
    }

    /** Rebuilt on every call, like a system built per request. */
    UntypedAgent perCall() {
        UntypedAgent inner = AgenticServices.loopBuilder().name("inner")
                .subAgents(AgenticServices.agentAction(scope -> scope.writeState("seen", true))).maxIterations(1).build();
        return AgenticServices.sequenceBuilder().name("perCall").subAgents(inner).build();
    }
}
