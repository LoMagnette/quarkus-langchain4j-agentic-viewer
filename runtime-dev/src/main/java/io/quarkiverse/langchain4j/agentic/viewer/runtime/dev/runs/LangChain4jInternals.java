package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every internal part of langchain4j-agentic, langchain4j-agentic-patterns and quarkus-langchain4j-agentic the live
 * view depends on (there is no public API for them yet: upstream U3, U4, U5). The hooks take the names from here, so
 * this list is complete; {@link #problems} checks them against the libraries on the class path: a test runs it on
 * every build, and the Dev UI logs what is missing at startup. A missing part only disables the live information
 * that needs it.
 */
public final class LangChain4jInternals {

    /** Which library a dependency belongs to; pattern ones are only checked when the module is present. */
    public enum Library {
        AGENTIC,
        QUARKUS_AGENTIC,
        PATTERNS
    }

    /**
     * An internal class, field, method or constructor the viewer uses.
     *
     * @param member field or method name, {@code <init>} for a constructor, null for the class itself
     * @param parameterTypes parameter type names of a method or constructor, null for a field or class
     * @param usedBy what stops working without it
     */
    public record Dependency(Library library, String className, String member, List<String> parameterTypes,
            String usedBy) {

        @Override
        public String toString() {
            return className + (member == null ? "" : "." + member + (parameterTypes == null ? "" : parameterTypes))
                    + " (" + usedBy + ")";
        }
    }

    // langchain4j-agentic
    public static final String PLANNER_HANDLER = "dev.langchain4j.agentic.internal.PlannerBasedInvocationHandler";
    public static final String PLANNER_HANDLER_SERVICE = "service";
    public static final String PLANNER_HANDLER_SUBAGENTS = "subagents";
    public static final String PLANNER_HANDLER_PLANNER_SUPPLIER = "plannerSupplier";
    public static final String SERVICE_BUILDER = "dev.langchain4j.agentic.internal.AbstractServiceBuilder";
    public static final String SERVICE_BUILDER_SUBAGENTS = "subagents";
    public static final String SCOPE = "dev.langchain4j.agentic.scope.DefaultAgenticScope";
    public static final String SCOPE_ERROR_HANDLER = "errorHandler";
    /** Package of the built-in workflow planners (sequence, loop, parallel, parallel mapper, conditional). */
    public static final String BUILT_IN_PLANNERS_PACKAGE = "dev.langchain4j.agentic.workflow.impl.";
    public static final String SEQUENTIAL_PLANNER = BUILT_IN_PLANNERS_PACKAGE + "SequentialPlanner";
    public static final String PARALLEL_PLANNER = BUILT_IN_PLANNERS_PACKAGE + "ParallelPlanner";
    public static final String PARALLEL_MAPPER_PLANNER = BUILT_IN_PLANNERS_PACKAGE + "ParallelMapperPlanner";
    public static final String LOOP_PLANNER = BUILT_IN_PLANNERS_PACKAGE + "LoopPlanner";
    public static final String CONDITIONAL_PLANNER = BUILT_IN_PLANNERS_PACKAGE + "ConditionalPlanner";
    public static final String SUPERVISOR_PLANNER = "dev.langchain4j.agentic.supervisor.SupervisorPlanner";
    public static final String NO_OP_ACTION = "NoOpAction";
    public static final String DONE_WITH_RESULT_ACTION = "DoneWithResultAction";
    // quarkus-langchain4j-agentic
    public static final String QUARKUS_AGENT = "io.quarkiverse.langchain4j.agentic.runtime.AbstractQuarkusAgent";
    public static final String QUARKUS_AGENT_AGENT = "agent";
    // langchain4j-agentic-patterns
    public static final String P2P_MAX_INVOCATIONS = "maxAgentsInvocations";
    public static final String P2P_EXIT_CONDITION = "exitCondition";
    public static final String BLACKBOARD_ACTIVATORS = "agentActivators";
    public static final String BLACKBOARD_STRATEGY = "conflictResolutionStrategy";
    public static final String BLACKBOARD_GOAL = "goalPredicate";
    public static final String BLACKBOARD_MAX_INVOCATIONS = "maxInvocations";
    public static final String BLACKBOARD_ACTIVATOR = "dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner$AgentActivator";
    public static final String BLACKBOARD_ACTIVATOR_AGENT = "agent";
    public static final String BLACKBOARD_ACTIVATOR_CAN_ACTIVATE = "canActivate";
    /** {@code executionState()} key of the P2P and blackboard planners' invocation counter. */
    public static final String INVOCATION_COUNTER = "invocationCounter";

    private static final String P2P = "dev.langchain4j.agentic.patterns.p2p.P2PPlanner";
    private static final String BLACKBOARD = "dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner";
    private static final String SCOPE_INTERFACE = "dev.langchain4j.agentic.scope.AgenticScope";
    private static final String LISTENER = "dev.langchain4j.agentic.observability.AgentListener";
    private static final String AGENT_INSTANCE = "dev.langchain4j.agentic.planner.AgentInstance";
    private static final String ACTION = "dev.langchain4j.agentic.planner.Action";
    private static final String RUNS = "runs (agent events)";
    private static final String HUMAN = "human-in-the-loop answers";
    private static final String DECISIONS = "planner decisions";
    private static final String ERRORS = "@ErrorHandler events";
    private static final String BUILDER_KINDS = "kinds of builder system nodes";

    public static final List<Dependency> DEPENDENCIES = List.of(
            // the listener of every run, inherited by the sub-agents (RootListeners)
            method(Library.AGENTIC, "dev.langchain4j.agentic.internal.InternalAgent", "registerInheritedParentListener",
                    RUNS, LISTENER),
            field(Library.QUARKUS_AGENTIC, QUARKUS_AGENT, QUARKUS_AGENT_AGENT, RUNS + ", " + HUMAN + ", " + DECISIONS),
            // the agent tree (AgentTrees)
            field(Library.AGENTIC, PLANNER_HANDLER, PLANNER_HANDLER_SERVICE, HUMAN + ", " + DECISIONS),
            field(Library.AGENTIC, PLANNER_HANDLER, PLANNER_HANDLER_SUBAGENTS, HUMAN),
            field(Library.AGENTIC, SERVICE_BUILDER, SERVICE_BUILDER_SUBAGENTS, HUMAN + ", " + DECISIONS),
            // human-in-the-loop steps (HumanInTheLoopSteps)
            method(Library.AGENTIC, "dev.langchain4j.agentic.internal.AgentExecutor", "<init>", HUMAN,
                    "dev.langchain4j.agentic.internal.AgentInvoker", "java.lang.Object"),
            method(Library.AGENTIC, "dev.langchain4j.agentic.internal.AgentExecutor", "agentInvoker", HUMAN),
            method(Library.AGENTIC, "dev.langchain4j.agentic.internal.AgentExecutor", "agent", HUMAN),
            method(Library.AGENTIC, "dev.langchain4j.agentic.internal.AgentInvoker", "invoke", HUMAN, SCOPE,
                    "java.lang.Object", "dev.langchain4j.agentic.internal.AgentInvocationArguments"),
            method(Library.AGENTIC, "dev.langchain4j.agentic.internal.AgentInvocationArguments", "namedArgs", HUMAN),
            cls(Library.AGENTIC, "dev.langchain4j.agentic.workflow.HumanInTheLoop", HUMAN),
            method(Library.AGENTIC, "dev.langchain4j.agentic.observability.ListenerNotifierUtil", "beforeAgentInvocation",
                    HUMAN, LISTENER, SCOPE_INTERFACE, AGENT_INSTANCE, "java.util.Map"),
            method(Library.AGENTIC, "dev.langchain4j.agentic.observability.ListenerNotifierUtil", "afterAgentInvocation",
                    HUMAN, LISTENER, SCOPE_INTERFACE, AGENT_INSTANCE, "java.util.Map", "java.lang.Object"),
            method(Library.AGENTIC, "dev.langchain4j.agentic.observability.ListenerNotifierUtil", "agentError", HUMAN,
                    LISTENER, SCOPE_INTERFACE, AGENT_INSTANCE, "java.util.Map", "java.lang.Throwable"),
            // planner decisions (PlannerDecisions, DevUiAgentListener)
            field(Library.AGENTIC, PLANNER_HANDLER, PLANNER_HANDLER_PLANNER_SUPPLIER, DECISIONS),
            cls(Library.AGENTIC, SEQUENTIAL_PLANNER, DECISIONS + " (built-in planners left alone)"),
            cls(Library.AGENTIC, ACTION + "$" + NO_OP_ACTION, DECISIONS),
            cls(Library.AGENTIC, ACTION + "$" + DONE_WITH_RESULT_ACTION, DECISIONS),
            // errors handled by an @ErrorHandler (ErrorHandlers)
            field(Library.AGENTIC, SCOPE, SCOPE_ERROR_HANDLER, ERRORS),
            // builder systems read at runtime (BuilderTopology): the planner class tells a workflow's kind
            cls(Library.AGENTIC, SEQUENTIAL_PLANNER, BUILDER_KINDS),
            cls(Library.AGENTIC, PARALLEL_PLANNER, BUILDER_KINDS),
            cls(Library.AGENTIC, PARALLEL_MAPPER_PLANNER, BUILDER_KINDS),
            cls(Library.AGENTIC, LOOP_PLANNER, BUILDER_KINDS),
            cls(Library.AGENTIC, CONDITIONAL_PLANNER, BUILDER_KINDS),
            cls(Library.AGENTIC, SUPERVISOR_PLANNER, BUILDER_KINDS),
            // pattern adapters
            method(Library.PATTERNS, "dev.langchain4j.agentic.patterns.goap.GoalOrientedSearchGraph", "<init>",
                    "GOAP path preview and live path", "java.util.List"),
            method(Library.PATTERNS, "dev.langchain4j.agentic.patterns.goap.GoalOrientedSearchGraph", "search",
                    "GOAP path preview and live path", "java.util.Collection", "java.lang.String"),
            field(Library.PATTERNS, P2P, P2P_MAX_INVOCATIONS, "P2P maximum and termination reason"),
            field(Library.PATTERNS, P2P, P2P_EXIT_CONDITION, "P2P termination reason"),
            field(Library.PATTERNS, BLACKBOARD, BLACKBOARD_ACTIVATORS, "blackboard candidates"),
            field(Library.PATTERNS, BLACKBOARD, BLACKBOARD_STRATEGY, "blackboard strategy"),
            field(Library.PATTERNS, BLACKBOARD, BLACKBOARD_GOAL, "blackboard goal status and termination"),
            field(Library.PATTERNS, BLACKBOARD, BLACKBOARD_MAX_INVOCATIONS, "blackboard maximum"),
            field(Library.PATTERNS, BLACKBOARD_ACTIVATOR, BLACKBOARD_ACTIVATOR_AGENT, "blackboard candidates"),
            method(Library.PATTERNS, BLACKBOARD_ACTIVATOR, BLACKBOARD_ACTIVATOR_CAN_ACTIVATE, "blackboard candidates",
                    SCOPE_INTERFACE));

    private LangChain4jInternals() {
    }

    private static Dependency cls(Library library, String className, String usedBy) {
        return new Dependency(library, className, null, null, usedBy);
    }

    private static Dependency field(Library library, String className, String name, String usedBy) {
        return new Dependency(library, className, name, null, usedBy);
    }

    private static Dependency method(Library library, String className, String name, String usedBy,
            String... parameterTypes) {
        return new Dependency(library, className, name, List.of(parameterTypes), usedBy);
    }

    /**
     * @return the dependencies missing from the libraries a class loader sees, empty when all are there; pattern
     *         dependencies are skipped when langchain4j-agentic-patterns is absent
     */
    public static List<String> problems(ClassLoader classLoader) {
        return problems(DEPENDENCIES, classLoader);
    }

    public static List<String> problems(List<Dependency> dependencies, ClassLoader classLoader) {
        boolean patterns = present(P2P, classLoader);
        List<String> problems = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            if (dependency.library() == Library.PATTERNS && !patterns) {
                continue;
            }
            String problem = check(dependency, classLoader);
            if (problem != null) {
                problems.add(dependency + ": " + problem);
            }
        }
        return problems;
    }

    private static boolean present(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static String check(Dependency dependency, ClassLoader classLoader) {
        Class<?> type;
        try {
            type = Class.forName(dependency.className(), false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            return "class not found";
        }
        if (dependency.member() == null) {
            return null;
        }
        if (dependency.parameterTypes() == null) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (field.getName().equals(dependency.member())) {
                        return null;
                    }
                }
            }
            return "field not found";
        }
        if (dependency.member().equals("<init>")) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (sameParameters(constructor.getParameterTypes(), dependency.parameterTypes())) {
                    return null;
                }
            }
            return "constructor not found";
        }
        List<Class<?>> types = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            types.add(c);
        }
        types.addAll(Arrays.asList(type.getInterfaces()));
        for (Class<?> c : types) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(dependency.member())
                        && sameParameters(method.getParameterTypes(), dependency.parameterTypes())) {
                    return null;
                }
            }
        }
        return "method not found";
    }

    private static boolean sameParameters(Class<?>[] actual, List<String> expected) {
        return Arrays.stream(actual).map(Class::getName).toList().equals(expected);
    }
}
