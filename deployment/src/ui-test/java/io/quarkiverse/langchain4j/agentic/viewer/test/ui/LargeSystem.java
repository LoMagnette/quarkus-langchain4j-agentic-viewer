package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;

/**
 * Fixture app of the performance check (T6.3): a generated builder system of 50 nodes, a sequence of 3 agents, 4
 * parallel groups of 6, 2 loops of 5 (2 iterations) and a conditional of 5; every agent takes 10 ms.
 */
@ApplicationScoped
public class LargeSystem {

    static final String NAME = "large";
    static final int NODES = 50;

    private static Object agent(String key) {
        return AgenticServices.agentAction(scope -> {
            Thread.sleep(10);
            scope.writeState(key, key);
        });
    }

    private static List<Object> agents(String prefix, int count) {
        List<Object> agents = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            agents.add(agent(prefix + i));
        }
        return agents;
    }

    @Produces
    @ApplicationScoped
    @Named(NAME)
    UntypedAgent large() {
        List<Object> steps = new ArrayList<>(agents("step", 3));
        for (int group = 0; group < 4; group++) {
            steps.add(AgenticServices.parallelBuilder().name("group" + group).subAgents(agents("g" + group + "_", 6)).build());
        }
        for (int loop = 0; loop < 2; loop++) {
            steps.add(AgenticServices.loopBuilder().name("loop" + loop).subAgents(agents("l" + loop + "_", 5))
                    .maxIterations(2).build());
        }
        steps.add(AgenticServices.conditionalBuilder().name("choice").subAgents(scope -> true, agents("c", 5).toArray()).build());
        return AgenticServices.sequenceBuilder().name(NAME).subAgents(steps).build();
    }
}
