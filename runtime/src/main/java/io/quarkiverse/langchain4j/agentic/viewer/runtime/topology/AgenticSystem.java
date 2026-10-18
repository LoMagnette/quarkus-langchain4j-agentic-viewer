package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.List;
import java.util.Objects;

import io.quarkus.runtime.annotations.RecordableConstructor;

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
 * @param warnings what is worth a look in its wiring before running it (TopologyChecks)
 */
public record AgenticSystem(
        String id,
        String name,
        String rootNodeId,
        SystemOrigin origin,
        SourceRef sourceRef,
        List<SystemInput> inputs,
        AgentNode root,
        List<TopologyWarning> warnings) {

    /** Without warnings. */
    public AgenticSystem(String id, String name, String rootNodeId, SystemOrigin origin, SourceRef sourceRef,
            List<SystemInput> inputs, AgentNode root) {
        this(id, name, rootNodeId, origin, sourceRef, inputs, root, List.of());
    }

    /** The one the build-time recorder uses (a record with several constructors needs it told). */
    @RecordableConstructor
    public AgenticSystem {
        Objects.requireNonNull(root, "root");
        if (!root.id().equals(rootNodeId)) {
            throw new IllegalArgumentException("rootNodeId " + rootNodeId + " does not match root node " + root.id());
        }
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
