package io.quarkiverse.langchain4j.agentic.viewer.runtime.spi;

/**
 * An extra node drawn inside a planner node.
 *
 * @param id identifier, unique within the contribution; edges refer to it
 * @param label text shown on the node
 */
public record ContributedNode(String id, String label) {
}
