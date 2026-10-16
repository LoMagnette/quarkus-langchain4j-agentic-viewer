package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Reports the decisions of planners (T3.2): each Action a supervisor, {@code @PlannerSupplier} or pattern planner
 * returns becomes a PlannerDecision event of the Dev UI run. langchain4j has no callback for it yet (upstream U3).
 * <p>
 * Every workflow takes a fresh planner from its {@code plannerSupplier} per invocation; the supplier is replaced by one
 * wrapping each planner in a {@link ReportingPlanner}, except the built-in workflow planners (sequence, loop, parallel,
 * parallel mapper, conditional), whose decisions the agent events already tell.
 * <p>
 * Internal API (the private final {@code PlannerBasedInvocationHandler.plannerSupplier}, set by reflection: fine on
 * JDK 25, JEP 500 may warn on later JDKs), isolated here: if it fails, decisions are simply not reported.
 */
final class PlannerDecisions {

    private static final Logger LOG = Logger.getLogger(PlannerDecisions.class);
    private static final String BUILT_IN_PLANNERS = "dev.langchain4j.agentic.workflow.impl.";
    private static volatile boolean warned;

    private PlannerDecisions() {
    }

    /**
     * Makes the planners of a root agent and of all the workflows under it report their decisions.
     *
     * @return the number of workflows whose planner supplier was decorated
     */
    @SuppressWarnings("unchecked")
    static int decorate(Object root, DevUiAgentListener listener) {
        try {
            int decorated = 0;
            for (Object handler : AgentTrees.handlers(root)) {
                Field field = AgentTrees.field(handler.getClass(), "plannerSupplier");
                Supplier<Planner> original = (Supplier<Planner>) field.get(handler);
                if (original != null) {
                    field.set(handler, (Supplier<Planner>) () -> report(original.get(), listener));
                    decorated++;
                }
            }
            return decorated;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                LOG.warnf(e, "Agentic viewer: cannot observe the planners of %s, planner decisions will not show",
                        root.getClass().getName());
            }
            return 0;
        }
    }

    private static Planner report(Planner planner, DevUiAgentListener listener) {
        return planner == null || planner.getClass().getName().startsWith(BUILT_IN_PLANNERS) ? planner
                : new ReportingPlanner(planner, listener);
    }

    /**
     * Delegates everything to the planner and reports each Action it returns for a Dev UI run of the listener's manager.
     */
    static final class ReportingPlanner implements Planner {

        private final Planner delegate;
        private final DevUiAgentListener listener;
        private volatile AgentInstance plannerAgent;
        private volatile List<AgentInstance> subagents = List.of();

        ReportingPlanner(Planner delegate, DevUiAgentListener listener) {
            this.delegate = delegate;
            this.listener = listener;
        }

        @Override
        public void init(InitPlanningContext context) {
            plannerAgent = context.plannerAgent();
            subagents = context.subagents() == null ? List.of() : List.copyOf(context.subagents());
            delegate.init(context);
        }

        @Override
        public Action firstAction(PlanningContext context) {
            return reported(context, delegate.firstAction(context));
        }

        @Override
        public Action nextAction(PlanningContext context) {
            return reported(context, delegate.nextAction(context));
        }

        private Action reported(PlanningContext context, Action action) {
            AgenticScope scope = context == null ? null : context.agenticScope();
            Run run = listener.run(scope);
            if (run != null && action != null && plannerAgent != null) {
                try {
                    listener.plannerDecision(run, plannerAgent, subagents, action);
                } catch (RuntimeException e) {
                    LOG.debugf(e, "Agentic viewer: cannot report a decision of %s", plannerAgent.name());
                }
            }
            return action;
        }

        @Override
        public Map<String, Object> executionState() {
            return delegate.executionState();
        }

        @Override
        public void restoreExecutionState(Map<String, Object> state) {
            delegate.restoreExecutionState(state);
        }

        @Override
        public AgenticSystemTopology topology() {
            return delegate.topology();
        }

        @Override
        public boolean terminated() {
            return delegate.terminated();
        }

        @Override
        public <T extends AgentInstance> T as(Class<T> type, AgentInstance agent) {
            return delegate.as(type, agent);
        }

        @Override
        public String toString() {
            return "ReportingPlanner[" + delegate + "]";
        }
    }
}
