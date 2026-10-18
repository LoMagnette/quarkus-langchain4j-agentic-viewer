package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderTopology;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.AgentStarted;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PlannerDecision;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;

/**
 * The Dev UI hooks of a root are installed once, whatever the number of Dev UI listeners attached to it (several run
 * managers, as tests have): no wrapper stacks on another, each run is reported by its own manager only, and a listener
 * of a shut down manager leaves.
 */
class RootListenersOnceTest {

    /** Calls its one sub-agent, then is done. */
    public static class OneStep implements Planner {
        private AgentInstance step;

        @Override
        public void init(InitPlanningContext context) {
            step = context.subagents().get(0);
        }

        @Override
        public Action firstAction(PlanningContext context) {
            return call(step);
        }

        @Override
        public Action nextAction(PlanningContext context) {
            return done();
        }
    }

    @Test
    void hooksAreInstalledOncePerRoot() throws Exception {
        UntypedAgent root = AgenticServices.plannerBuilder().name("once").planner(OneStep::new)
                .subAgents(AgenticServices.agentAction(scope -> scope.writeState("done", true))).outputKey("done").build();
        AgenticSystem system = BuilderTopology.system("once", "once", new SourceRef("once", "once", null), (AgentInstance) root);
        List<RunManager> managers = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                RunManager manager = new RunManager(id -> Optional.of(system), (s, inputs) -> root.invoke(inputs));
                managers.add(manager);
                assertTrue(RootListeners.attach("once", root, new DevUiAgentListener(manager)));
            }

            Planner planner = plannerOf(root);
            assertInstanceOf(PlannerDecisions.ReportingPlanner.class, planner);
            Field delegate = PlannerDecisions.ReportingPlanner.class.getDeclaredField("delegate");
            delegate.setAccessible(true);
            assertFalse(delegate.get(planner) instanceof PlannerDecisions.ReportingPlanner,
                    "one reporting planner, not one per listener");

            Run last = run(managers.get(2));
            assertEquals(2, last.events().stream().filter(PlannerDecision.class::isInstance).count(), last.events()::toString);
            assertEquals(2, last.events().stream().filter(AgentStarted.class::isInstance).count(), last.events()::toString);
            assertEquals(List.of(), managers.get(0).listRuns("once"), "nothing went to another manager");

            managers.get(0).shutdown();
            managers.get(1).shutdown();
            assertEquals(2, run(managers.get(2)).events().stream().filter(PlannerDecision.class::isInstance).count());
        } finally {
            managers.forEach(RunManager::shutdown);
        }
    }

    private static Run run(RunManager manager) throws Exception {
        Run run = manager.run(manager.startRun("once", Map.of())).orElseThrow();
        run.completion().get(10, TimeUnit.SECONDS);
        assertEquals(RunStatus.SUCCEEDED, run.status(), run.events()::toString);
        return run;
    }

    /** What the root's (decorated) planner supplier gives an invocation. */
    @SuppressWarnings("unchecked")
    private static Planner plannerOf(Object root) throws Exception {
        for (Object handler : AgentTrees.handlers(root)) {
            Field field = AgentTrees.field(handler.getClass(), LangChain4jInternals.PLANNER_HANDLER_PLANNER_SUPPLIER);
            return ((Supplier<Planner>) field.get(handler)).get();
        }
        throw new AssertionError("no workflow handler");
    }
}
