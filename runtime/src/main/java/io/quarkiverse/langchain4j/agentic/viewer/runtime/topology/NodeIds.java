package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * The rule of topology node ids, the one the build-time topology, the builder topology and the run events of the
 * Dev UI follow (a run only lines up with its graph while they agree): the root node id is the system id, a sub-agent
 * node id is its parent id, {@code /}, and the simple name of the sub-agent's class, with {@code ~n} for the n-th
 * (n &gt; 1) sub-agent of that class under the same parent.
 */
public final class NodeIds {

    public static final String SEPARATOR = "/";
    public static final String REPEAT = "~";

    private NodeIds() {
    }

    /** {@code com.example.Outer$Inner} -&gt; {@code Inner} */
    public static String simpleName(String className) {
        return className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1);
    }

    /** The simple name of a class, {@code ?} for none. */
    public static String simpleName(Class<?> type) {
        return simpleName(type == null ? "?" : type.getName());
    }

    /**
     * @param segment the simple name of the sub-agent's class
     * @param occurrence 1 for the first sub-agent of that class under the parent, n for the n-th
     */
    public static String child(String parentId, String segment, int occurrence) {
        return parentId + SEPARATOR + segment + (occurrence > 1 ? REPEAT + occurrence : "");
    }
}
