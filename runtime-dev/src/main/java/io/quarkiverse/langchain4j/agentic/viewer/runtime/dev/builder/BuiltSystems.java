package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import dev.langchain4j.agentic.planner.AgentInstance;

/**
 * The agentic systems the application built, reported by the dev-mode transform of {@code builder-discovery=transform}
 * (T5.4): its rewritten classes call {@link #register} after each builder's {@code build()}. Systems built several
 * times (e.g. per request) do not flood the list (T5.5a): one entry per call site and shape (kinds, names and types of
 * the agents), holding the latest instance. Held strongly: a system built per request is garbage once the request
 * ends, and must stay listed and runnable; the number of entries is bounded by the call sites. Called from application
 * code: never fails.
 */
public final class BuiltSystems {

    /** Shapes kept per call site: a builder producing ever new shapes does not grow without bound. */
    static final int MAX_SHAPES = 10;

    // call site -> shape -> latest instance, shapes in the order they first appeared
    private static final Map<String, Map<String, Object>> BUILT = new TreeMap<>();

    private BuiltSystems() {
    }

    /**
     * @param built what a builder's build() returned
     * @param callSite {@code Class#method:line} of the call
     */
    public static void register(Object built, String callSite) {
        try {
            if (built instanceof AgentInstance agent && callSite != null) {
                String shape = shape(agent);
                synchronized (BUILT) {
                    Map<String, Object> shapes = BUILT.computeIfAbsent(callSite, site -> new LinkedHashMap<>());
                    if (shapes.containsKey(shape) || shapes.size() < MAX_SHAPES) {
                        shapes.put(shape, built);
                    }
                }
            }
        } catch (RuntimeException e) {
            // the application goes on: only the Dev UI misses this system
        }
    }

    /**
     * @return the latest instance by entry: the call site for its first shape, {@code callSite~n} for the n-th
     */
    public static Map<String, Object> latest() {
        Map<String, Object> latest = new LinkedHashMap<>();
        synchronized (BUILT) {
            BUILT.forEach((callSite, shapes) -> {
                int index = 0;
                for (Object built : shapes.values()) {
                    index++;
                    latest.put(index == 1 ? callSite : callSite + "~" + index, built);
                }
            });
        }
        return latest;
    }

    /** Forgets everything (the application stops or reloads). */
    public static void clear() {
        synchronized (BUILT) {
            BUILT.clear();
        }
    }

    /** The structure of a system: what its graph shows, not its runtime ids. */
    static String shape(AgentInstance agent) {
        StringBuilder shape = new StringBuilder();
        shape(agent, shape);
        return shape.toString();
    }

    private static void shape(AgentInstance agent, StringBuilder shape) {
        shape.append(agent.topology()).append(':').append(agent.name()).append(':')
                .append(agent.type() == null ? null : agent.type().getName()).append(':')
                .append(agent.plannerType() == null ? null : agent.plannerType().getName()).append(':')
                .append(agent.outputKey()).append('[');
        agent.subagents().forEach(sub -> shape(sub, shape));
        shape.append(']');
    }
}
