package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.patterns.blackboard.ConflictResolutionStrategy;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.LangChain4jInternals;

/**
 * Blackboard planners of langchain4j-agentic-patterns. Registered only when the module is present.
 * <p>
 * The planner lets its agents (knowledge sources) fire when their inputs are on the blackboard (the scope) and one
 * changed since they last fired; among those candidates, its conflict resolution strategy picks one; it stops when
 * its goal predicate holds or no agent can fire (quiescent). The adapter tells, per decision, the candidates (the
 * picked one included), the strategy, the invocations against the maximum and whether the goal holds, and on done why
 * it stopped. Candidates, strategy, goal and maximum are private: read by reflection (internal API: without them,
 * only the invocations are told). langchain4j has no callback for the candidates yet (upstream U3).
 */
@ApplicationScoped
public class BlackboardAdapter implements PatternAdapter {

    public static final String GOAL_REACHED = "GOAL_REACHED";
    public static final String QUIESCENT = "QUIESCENT";

    private static final Field ACTIVATORS = field(BlackboardPlanner.class, LangChain4jInternals.BLACKBOARD_ACTIVATORS);
    private static final Field STRATEGY = field(BlackboardPlanner.class, LangChain4jInternals.BLACKBOARD_STRATEGY);
    private static final Field GOAL = field(BlackboardPlanner.class, LangChain4jInternals.BLACKBOARD_GOAL);
    private static final Field MAX = field(BlackboardPlanner.class, LangChain4jInternals.BLACKBOARD_MAX_INVOCATIONS);
    private static final Class<?> ACTIVATOR = activatorClass();
    private static final Field ACTIVATOR_AGENT = ACTIVATOR == null ? null : field(ACTIVATOR, LangChain4jInternals.BLACKBOARD_ACTIVATOR_AGENT);
    private static final Method CAN_ACTIVATE = ACTIVATOR == null ? null : canActivate();

    private static Class<?> activatorClass() {
        try {
            return Class.forName(LangChain4jInternals.BLACKBOARD_ACTIVATOR, false,
                    BlackboardPlanner.class.getClassLoader());
        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static Method canActivate() {
        try {
            Method method = ACTIVATOR.getDeclaredMethod(LangChain4jInternals.BLACKBOARD_ACTIVATOR_CAN_ACTIVATE, AgenticScope.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public PatternType patternType() {
        return PatternType.BLACKBOARD;
    }

    @Override
    public boolean handles(Planner planner) {
        return planner instanceof BlackboardPlanner;
    }

    @Override
    public Insight insight(Decision decision) {
        Planner planner = decision.planner();
        Map<String, Object> extras = new HashMap<>();
        extras.put("invocations", planner.executionState().get(LangChain4jInternals.INVOCATION_COUNTER));
        Object max = get(MAX, planner);
        if (max != null) {
            extras.put("maxInvocations", max);
        }
        String strategy = strategy(get(STRATEGY, planner));
        if (strategy != null) {
            extras.put("strategy", strategy);
        }
        Boolean goal = goalHolds(planner, decision.scope());
        if (goal != null) {
            extras.put("goalReached", goal);
        }
        if (decision.action().isDone()) {
            extras.put("termination", Boolean.TRUE.equals(goal) ? GOAL_REACHED : QUIESCENT);
            return new Insight(extras, Map.of());
        }
        List<AgentInstance> candidates = candidates(planner, decision);
        return candidates == null ? new Insight(extras, Map.of()) : new Insight(extras, Map.of("candidates", candidates));
    }

    /**
     * The agents that could fire for this decision: those still able to fire, plus the one picked (picking it marks it
     * fired), in declaration order.
     */
    private static List<AgentInstance> candidates(Planner planner, Decision decision) {
        if (!(get(ACTIVATORS, planner) instanceof Map<?, ?> activators) || ACTIVATOR_AGENT == null || CAN_ACTIVATE == null) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        if (decision.action() instanceof Action.AgentCallAction call) {
            call.agentsToCall().forEach(agent -> ids.add(agent.agentId()));
        }
        try {
            for (Object activator : activators.values()) {
                if (Boolean.TRUE.equals(CAN_ACTIVATE.invoke(activator, decision.scope()))) {
                    ids.add(((AgentInstance) ACTIVATOR_AGENT.get(activator)).agentId());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return decision.subagents().stream().filter(agent -> ids.contains(agent.agentId())).toList();
    }

    @SuppressWarnings("unchecked")
    private static Boolean goalHolds(Planner planner, AgenticScope scope) {
        try {
            return scope != null && get(GOAL, planner) instanceof Predicate<?> goal
                    ? ((Predicate<AgenticScope>) goal).test(scope)
                    : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "declaration order", or the class the strategy is written in (e.g. a lambda of the application). */
    private static String strategy(Object strategy) {
        if (strategy == null) {
            return null;
        }
        if (strategy == ConflictResolutionStrategy.DECLARATION_ORDER) {
            return "declaration order";
        }
        String name = strategy.getClass().getName();
        int lambda = name.indexOf("$$Lambda");
        String declaring = lambda < 0 ? name : name.substring(0, lambda);
        return declaring.equals(ConflictResolutionStrategy.class.getName()) ? "built-in strategy"
                : "custom (" + declaring.substring(Math.max(declaring.lastIndexOf('.'), declaring.lastIndexOf('$')) + 1) + ")";
    }

    private static Object get(Field field, Object target) {
        try {
            return field == null ? null : field.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
