package io.quarkiverse.langchain4j.agentic.viewer.runtime.spi;

/**
 * An edge drawn inside a planner node.
 *
 * @param source a sub-agent of the planner (its agent name or node id) or a contributed node (its id)
 * @param target same as {@code source}
 * @param label text shown on the edge, null for none
 */
public record ContributedEdge(String source, String target, String label) {
}
