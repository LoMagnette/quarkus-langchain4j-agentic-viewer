package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * Something in the wiring of a system worth a look before running it.
 *
 * @param nodeId the node it is about
 * @param level a warning (certain: it will not behave as written) or a hint (likely, may be intended)
 * @param code stable identifier of the rule
 * @param message what it is and why it matters
 */
public record TopologyWarning(String nodeId, Level level, String code, String message) {

    public enum Level {
        WARNING,
        HINT
    }
}
