package io.quarkiverse.langchain4j.agentic.viewer.deployment.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentTree;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeIds;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PlannerInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;

/**
 * Turns the declarative root systems found at build time into the shared topology model.
 * <p>
 * Node ids only depend on class and method names, so they are stable across builds of the same code: the root node
 * id is the root descriptor id ({@code Class#method}), the sub-agents follow {@link NodeIds}.
 */
public class TopologyBuilder {

    /** Tells the planner behind a {@code @PlannerSupplier}; null: unknown (CUSTOM, no class). */
    private final PlannerTypeDetector plannerTypes;

    public TopologyBuilder() {
        this(null);
    }

    public TopologyBuilder(PlannerTypeDetector plannerTypes) {
        this.plannerTypes = plannerTypes;
    }

    /**
     * @param roots root systems, as found by the root system detector
     * @return one system per root, in the same order
     */
    public List<AgenticSystem> build(List<AgentTree> roots) {
        List<AgenticSystem> systems = new ArrayList<>();
        for (AgentTree root : roots) {
            AgentDescriptor descriptor = root.descriptor();
            AgentNode rootNode = node(descriptor.id(), root, null);
            systems.add(new AgenticSystem(
                    rootNode.id(),
                    simpleName(descriptor.declaringClass()),
                    rootNode.id(),
                    SystemOrigin.DECLARATIVE,
                    new SourceRef(descriptor.declaringClass(), descriptor.methodName(), null),
                    descriptor.inputs(),
                    rootNode));
        }
        return systems;
    }

    /**
     * @param guard activation condition given by the conditional parent, null otherwise
     */
    private AgentNode node(String id, AgentTree tree, Guard guard) {
        AgentDescriptor descriptor = tree.descriptor();
        if (descriptor == null) {
            // langchain4j cannot build it either, unless it is an agent kind this scanner does not read yet
            return new AgentNode(id, simpleName(tree.agentClass()), NodeKind.NON_AI_AGENT,
                    "No agent method found in " + tree.agentClass(), List.of(), null, false, false, List.of(), guard,
                    null, null, null);
        }
        List<AgentNode> children = new ArrayList<>();
        childIds(id, tree).forEach((childId, child) -> children.add(node(childId, child,
                descriptor.branchGuards().get(child.agentClass()))));
        return new AgentNode(
                id,
                descriptor.name(),
                descriptor.kind(),
                descriptor.description(),
                descriptor.inputKeys(),
                descriptor.outputKey(),
                descriptor.optional(),
                descriptor.async(),
                children,
                descriptor.kind() == NodeKind.LOOP && descriptor.loopGuard() != null
                        ? descriptor.loopGuard().withActivation(guard)
                        : guard,
                plannerInfo(descriptor),
                descriptor.aiDetails(),
                descriptor.remoteDetails(),
                descriptor.code());
    }

    /** The sub-agents of a node by their node id: {@code /<simple class name>}, {@code ~n} for a repeated sibling. */
    private static Map<String, AgentTree> childIds(String id, AgentTree tree) {
        Map<String, AgentTree> children = new LinkedHashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (AgentTree child : tree.children()) {
            String segment = simpleName(child.agentClass());
            int occurrence = occurrences.merge(segment, 1, Integer::sum);
            children.put(NodeIds.child(id, segment, occurrence), child);
        }
        return children;
    }

    /**
     * @return the agent class of every node of the systems, by node id (the ids {@link #build} gives)
     */
    public static Map<String, String> agentClasses(List<AgentTree> roots) {
        Map<String, String> classes = new LinkedHashMap<>();
        roots.forEach(root -> agentClasses(root.descriptor().id(), root, classes));
        return classes;
    }

    private static void agentClasses(String id, AgentTree tree, Map<String, String> classes) {
        classes.put(id, tree.agentClass());
        childIds(id, tree).forEach((childId, child) -> agentClasses(childId, child, classes));
    }

    /**
     * The planner a declarative {@code @PlannerSupplier} creates, as far as its bytecode tells (PlannerTypeDetector).
     */
    private PlannerInfo plannerInfo(AgentDescriptor descriptor) {
        if (descriptor.kind() != NodeKind.PLANNER) {
            return null;
        }
        if (plannerTypes != null && descriptor.plannerSupplier() != null) {
            return plannerTypes.detect(descriptor.plannerSupplier());
        }
        return new PlannerInfo(null, PatternType.CUSTOM,
                descriptor.plannerSupplier() == null ? Map.of() : Map.of("supplier", descriptor.plannerSupplier()));
    }

    /** {@code com.example.Outer$Inner} -> {@code Inner} */
    private static String simpleName(String className) {
        return NodeIds.simpleName(className);
    }
}
