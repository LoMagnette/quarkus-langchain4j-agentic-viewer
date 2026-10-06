package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.List;
import java.util.Objects;

/**
 * A root agentic system and its full topology.
 *
 * @param id stable identifier of the system
 * @param name display name
 * @param rootNodeId id of the root node, always {@code root.id()}
 * @param origin how the system is defined
 * @param sourceRef where the system is defined
 * @param inputs inputs of the system, in declaration order
 * @param root root node of the topology tree
 */
public record AgenticSystem(
        String id,
        String name,
        String rootNodeId,
        SystemOrigin origin,
        SourceRef sourceRef,
        List<SystemInput> inputs,
        AgentNode root) {

    public AgenticSystem {
        Objects.requireNonNull(root, "root");
        if (!root.id().equals(rootNodeId)) {
            throw new IllegalArgumentException("rootNodeId " + rootNodeId + " does not match root node " + root.id());
        }
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
