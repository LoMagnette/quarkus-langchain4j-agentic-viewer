package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.patterns.p2p.P2PPlanner;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.LangChain4jInternals;

/**
 * Peer-to-peer planners of langchain4j-agentic-patterns. Registered only when the module is present.
 * <p>
 * The planner calls the agents whose inputs are in the scope, again as their inputs change, until its exit condition
 * holds, it reached its maximum number of invocations, or no agent can activate (a stable state). The adapter tells,
 * per decision, the invocations so far against the maximum, the agents whose inputs are all present, and why the
 * planner stopped. The counter is public ({@code executionState}); the maximum and the exit condition are private
 * fields read by reflection (internal API: without them, the maximum and the reason are left out).
 */
@ApplicationScoped
public class P2PAdapter implements PatternAdapter {

    public static final String MAX_INVOCATIONS = "MAX_INVOCATIONS";
    public static final String EXIT_CONDITION = "EXIT_CONDITION";
    public static final String STABLE_STATE = "STABLE_STATE";

    private static final Field MAX = field(LangChain4jInternals.P2P_MAX_INVOCATIONS);
    private static final Field EXIT = field(LangChain4jInternals.P2P_EXIT_CONDITION);

    private static Field field(String name) {
        try {
            Field field = P2PPlanner.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public PatternType patternType() {
        return PatternType.P2P;
    }

    @Override
    public boolean handles(Planner planner) {
        return planner instanceof P2PPlanner;
    }

    /** The planner's exit condition, recording what it answered the planner (never called by the Dev UI). */
    static final class RecordedExit implements BiPredicate<AgenticScope, Integer> {
        final BiPredicate<AgenticScope, Integer> delegate;
        volatile Boolean last;

        RecordedExit(BiPredicate<AgenticScope, Integer> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean test(AgenticScope scope, Integer invocations) {
            boolean result = delegate.test(scope, invocations);
            last = result;
            return result;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void observe(Planner planner) {
        try {
            if (EXIT != null && EXIT.get(planner) instanceof BiPredicate<?, ?> exit && !(exit instanceof RecordedExit)) {
                EXIT.set(planner, new RecordedExit((BiPredicate<AgenticScope, Integer>) exit));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // the reason is then left out when the exit condition may be it
        }
    }

    @Override
    public Insight insight(Decision decision) {
        Map<String, Object> extras = new HashMap<>();
        Object counter = decision.planner().executionState().get(LangChain4jInternals.INVOCATION_COUNTER);
        Integer max = intField(MAX, decision.planner());
        extras.put("invocations", counter);
        if (max != null) {
            extras.put("maxInvocations", max);
        }
        AgenticScope scope = decision.scope();
        Set<String> keys = scope == null ? Set.of() : scope.state().keySet();
        List<AgentInstance> ready = decision.subagents().stream()
                .filter(agent -> agent.arguments().stream().map(AgentArgument::name).allMatch(keys::contains)).toList();
        if (decision.action().isDone() && counter instanceof Integer invocations) {
            String termination = termination(decision.planner(), invocations, max);
            if (termination != null) {
                extras.put("termination", termination);
            }
        }
        return new Insight(extras, Map.of("ready", ready));
    }

    /** Why the planner stopped, in the order P2PPlanner checks (nextAction: terminated, then no agent to activate). */
    private static String termination(Planner planner, int invocations, Integer max) {
        if (max != null && invocations >= max) {
            return MAX_INVOCATIONS;
        }
        Boolean exit = exitConditionHeld(planner);
        if (exit == null) {
            return null; // unknown: what the planner's exit condition answered was not recorded
        }
        return exit ? EXIT_CONDITION : STABLE_STATE;
    }

    /** What the exit condition answered the planner last (it checks it at each step), null when unknown. */
    private static Boolean exitConditionHeld(Planner planner) {
        try {
            return EXIT != null && EXIT.get(planner) instanceof RecordedExit recorded ? recorded.last : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Integer intField(Field field, Planner planner) {
        try {
            return field == null ? null : field.getInt(planner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
