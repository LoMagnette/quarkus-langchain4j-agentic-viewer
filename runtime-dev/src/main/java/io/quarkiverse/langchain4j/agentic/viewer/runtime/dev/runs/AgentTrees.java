package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.InternalAgent;

/**
 * Walks the agents langchain4j built for a root, through internal API (listed in {@link LangChain4jInternals}, used by
 * {@link HumanInTheLoopSteps} and {@link PlannerDecisions}): every workflow is a {@code PlannerBasedInvocationHandler}
 * whose builder ({@code AbstractServiceBuilder}) lists its sub-agent executors.
 */
final class AgentTrees {


    private AgentTrees() {
    }

    /**
     * @return the workflow handlers of a root agent and of all the workflows under it, root first
     */
    static List<Object> handlers(Object root) throws ReflectiveOperationException {
        List<Object> handlers = new ArrayList<>();
        collect(root, handlers, Collections.newSetFromMap(new IdentityHashMap<>()));
        return handlers;
    }

    private static void collect(Object agent, List<Object> handlers, Set<Object> seen) throws ReflectiveOperationException {
        Object handler = handlerOf(agent);
        if (handler == null || !seen.add(handler)) {
            return;
        }
        handlers.add(handler);
        for (Object subagent : serviceSubagents(handler)) {
            if (subagent instanceof AgentExecutor executor) {
                collect(executor.agent(), handlers, seen);
            }
        }
    }

    /**
     * The sub-agents a workflow's builder lists: a mutable list, which per-scope copies of the workflow take theirs from.
     */
    @SuppressWarnings("unchecked")
    static List<Object> serviceSubagents(Object handler) throws ReflectiveOperationException {
        Object service = field(handler.getClass(), LangChain4jInternals.PLANNER_HANDLER_SERVICE).get(handler);
        Class<?> builder = Class.forName(LangChain4jInternals.SERVICE_BUILDER, false, handler.getClass().getClassLoader());
        return (List<Object>) field(builder, LangChain4jInternals.SERVICE_BUILDER_SUBAGENTS).get(service);
    }

    /** The planner-based handler running a workflow agent: a JDK proxy's handler, or the delegate of a Quarkus agent. */
    static Object handlerOf(Object agent) throws ReflectiveOperationException {
        if (agent == null) {
            return null;
        }
        if (isHandler(agent)) {
            return agent;
        }
        if (Proxy.isProxyClass(agent.getClass())) {
            Object handler = Proxy.getInvocationHandler(agent);
            return isHandler(handler) ? handler : null;
        }
        // AbstractQuarkusAgent keeps the agent built by langchain4j in its "agent" field
        for (Class<?> type = agent.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().equals(LangChain4jInternals.QUARKUS_AGENT_AGENT) && InternalAgent.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object delegate = field.get(agent);
                    return delegate == agent ? null : handlerOf(delegate);
                }
            }
        }
        return null;
    }

    private static boolean isHandler(Object object) {
        return object.getClass().getName().equals(LangChain4jInternals.PLANNER_HANDLER);
    }

    static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
