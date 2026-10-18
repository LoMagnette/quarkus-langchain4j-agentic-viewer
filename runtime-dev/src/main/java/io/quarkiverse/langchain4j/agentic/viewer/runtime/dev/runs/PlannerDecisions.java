package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.Field;
import java.util.HashMap;
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
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.PatternAdapter;

/**
 * Reports the decisions of planners (T3.2): each Action a supervisor, {@code @PlannerSupplier} or pattern planner
 * returns becomes a PlannerDecision event of the Dev UI run. langchain4j has no callback for it yet (upstream U3).
 * <p>
 * Every workflow takes a fresh planner from its {@code plannerSupplier} per invocation; the supplier is replaced by one
 * wrapping each planner in a {@link ReportingPlanner}, except the built-in workflow planners (sequence, loop, parallel,
 * parallel mapper, conditional), whose decisions the agent events already tell. The planner a workflow handler keeps
 * for itself ({@code defaultPlannerInstance}, taken from the supplier by its constructor, also for the per-scope copies
 * langchain4j makes) stays the original one: {@code plannerType()} and a supervisor's chat memory access depend on it.
 * <p>
 * Internal API (the private final {@code PlannerBasedInvocationHandler.plannerSupplier}, set by reflection: fine on
 * JDK 25, JEP 500 may warn on later JDKs), isolated here: if it fails, decisions are simply not reported.
 */
final class PlannerDecisions {

    private static final Logger LOG = Logger.getLogger(PlannerDecisions.class);
    private static final StackWalker STACK = StackWalker.getInstance();
    private static volatile boolean warned;

    private PlannerDecisions() {
    }

    /**
     * Makes the planners of a root agent and of all the workflows under it report their decisions.
     *
     * @return the number of workflows whose planner supplier was decorated
     */
    @SuppressWarnings("unchecked")
    static int decorate(Object root, DevUiListeners listeners) {
        try {
            int decorated = 0;
            for (Object handler : AgentTrees.handlers(root)) {
                Field field = AgentTrees.field(handler.getClass(), LangChain4jInternals.PLANNER_HANDLER_PLANNER_SUPPLIER);
                Supplier<Planner> original = (Supplier<Planner>) field.get(handler);
                if (original != null) {
                    field.set(handler, (Supplier<Planner>) () -> {
                        Planner planner = original.get();
                        return forHandlerConstructor() ? planner : report(planner, listeners);
                    });
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

    /** Whether the supplier is called by a workflow handler's constructor, for its default planner instance. */
    private static boolean forHandlerConstructor() {
        return STACK.walk(frames -> frames.limit(6).anyMatch(frame -> frame.getMethodName().equals("<init>")
                && frame.getClassName().equals(LangChain4jInternals.PLANNER_HANDLER)));
    }

    private static Planner report(Planner planner, DevUiListeners listeners) {
        return planner == null || planner.getClass().getName().startsWith(LangChain4jInternals.BUILT_IN_PLANNERS_PACKAGE) ? planner
                : new ReportingPlanner(planner, listeners);
    }

    /**
     * Delegates everything to the planner and reports each Action it returns for a Dev UI run of one of the listeners'
     * managers: the one running the scope the planner is initialized with (a planner serves one invocation).
     */
    static final class ReportingPlanner implements Planner {

        private final Planner delegate;
        private final DevUiListeners listeners;
        /** the listener whose run this planner plans for, null outside Dev UI runs */
        private volatile DevUiAgentListener listener;
        /** what is known about the planner's pattern, null for a planner of no known pattern */
        private volatile PatternAdapter adapter;
        private volatile AgentInstance plannerAgent;
        private volatile List<AgentInstance> subagents = List.of();
        private volatile boolean decided;

        ReportingPlanner(Planner delegate, DevUiListeners listeners) {
            this.delegate = delegate;
            this.listeners = listeners;
        }

        Planner planner() {
            return delegate;
        }

        @Override
        public void init(InitPlanningContext context) {
            plannerAgent = context.plannerAgent();
            subagents = context.subagents() == null ? List.of() : List.copyOf(context.subagents());
            delegate.init(context);
            listener = listeners.owner(context.agenticScope());
            adapter = listener == null ? null : listener.patternAdapters().adapterFor(delegate).orElse(null);
            if (adapter != null) {
                try {
                    adapter.observe(planner());
                } catch (RuntimeException e) {
                    LOG.debugf(e, "Agentic viewer: the %s adapter cannot observe a planner", adapter.patternType());
                }
            }
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
            DevUiAgentListener listener = this.listener;
            Run run = listener == null ? null : listener.run(scope);
            if (run != null && action != null && plannerAgent != null) {
                try {
                    boolean first = !decided;
                    decided = true;
                    PatternAdapter.Insight insight = insight(action, scope, first);
                    listener.plannerDecision(run, plannerAgent, subagents, action, insight.extras(), insight.agents());
                } catch (RuntimeException e) {
                    LOG.debugf(e, "Agentic viewer: cannot report a decision of %s", plannerAgent.name());
                }
            }
            return action;
        }

        /** The pattern of the planner and what its adapter tells about the decision; a failing adapter tells nothing. */
        private PatternAdapter.Insight insight(Action action, AgenticScope scope, boolean first) {
            if (adapter == null) {
                return PatternAdapter.Insight.NONE;
            }
            PatternAdapter.Insight insight = PatternAdapter.Insight.NONE;
            try {
                insight = adapter.insight(new PatternAdapter.Decision(planner(), action, scope, plannerAgent, subagents, first));
            } catch (RuntimeException e) {
                LOG.debugf(e, "Agentic viewer: the %s adapter failed on a decision", adapter.patternType());
            }
            Map<String, Object> extras = new HashMap<>(insight == null ? Map.of() : insight.extras());
            extras.put("pattern", adapter.patternType().name());
            return new PatternAdapter.Insight(extras, insight == null ? null : insight.agents());
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
