package io.quarkiverse.langchain4j.agentic.viewer.deployment.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.TopologyChecks;

/**
 * Turns the declarative root systems found at build time into the shared topology model.
 * <p>
 * Node ids only depend on class and method names, so they are stable across builds of the same code: the root node
 * id is the root descriptor id ({@code Class#method}), the sub-agents follow {@link NodeIds}.
 */
public class TopologyBuilder {

    /** Tells the planner behind a {@code @PlannerSupplier}; null: unknown (CUSTOM, no class). */
    private final PlannerTypeDetector plannerTypes;
    private final int loopWarningIterations;

    public TopologyBuilder() {
        this(null);
    }

    public TopologyBuilder(PlannerTypeDetector plannerTypes) {
        this(plannerTypes, TopologyChecks.DEFAULT_LOOP_ITERATIONS);
    }

    /**
     * @param loopWarningIterations iterations above which a loop without exit condition is reported (TopologyChecks)
     */
    public TopologyBuilder(PlannerTypeDetector plannerTypes, int loopWarningIterations) {
        this.plannerTypes = plannerTypes;
        this.loopWarningIterations = loopWarningIterations;
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
            // what the shared checks cannot see in the topology: the branches langchain4j leaves out, and the keys the
            // @Output and condition methods read
            Map<String, List<String>> dropped = new LinkedHashMap<>();
            Set<String> methodReads = new LinkedHashSet<>();
            collectChecks(descriptor.id(), root, dropped, methodReads);
            systems.add(new AgenticSystem(
                    rootNode.id(),
                    simpleName(descriptor.declaringClass()),
                    rootNode.id(),
                    SystemOrigin.DECLARATIVE,
                    new SourceRef(descriptor.declaringClass(), descriptor.methodName(), null),
                    descriptor.inputs(),
                    rootNode,
                    TopologyChecks.check(rootNode, dropped, methodReads, loopWarningIterations)));
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
                plannerInfo(id, tree),
                descriptor.aiDetails(),
                descriptor.remoteDetails(),
                descriptor.code());
    }

    private static void collectChecks(String id, AgentTree tree, Map<String, List<String>> dropped, Set<String> methodReads) {
        AgentDescriptor descriptor = tree.descriptor();
        if (descriptor == null) {
            return;
        }
        methodReads.addAll(descriptor.methodReads());
        if (descriptor.kind() == NodeKind.CONDITIONAL) {
            List<String> without = descriptor.subAgents().stream()
                    .filter(subAgent -> !descriptor.branchGuards().containsKey(subAgent)).distinct().toList();
            if (!without.isEmpty()) {
                dropped.put(id, without);
            }
        }
        childIds(id, tree).forEach((childId, child) -> collectChecks(childId, child, dropped, methodReads));
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
    private PlannerInfo plannerInfo(String id, AgentTree tree) {
        AgentDescriptor descriptor = tree.descriptor();
        if (descriptor.kind() != NodeKind.PLANNER) {
            return null;
        }
        if (plannerTypes != null && descriptor.plannerSupplier() != null) {
            PlannerInfo info = plannerTypes.detect(descriptor.plannerSupplier());
            if (info.patternType() != PatternType.BDI) {
                return info;
            }
            // a desire names its agents by class: the first sub-agent of that class (langchain4j keeps one per type)
            Map<String, String> nodeIds = new HashMap<>();
            childIds(id, tree).forEach((childId, child) -> nodeIds.putIfAbsent(child.agentClass(), childId));
            return info.withDesires(plannerTypes.desires(descriptor.plannerSupplier(), nodeIds::get));
        }
        return new PlannerInfo(null, PatternType.CUSTOM,
                descriptor.plannerSupplier() == null ? Map.of() : Map.of("supplier", descriptor.plannerSupplier()));
    }

    /** {@code com.example.Outer$Inner} -> {@code Inner} */
    private static String simpleName(String className) {
        return NodeIds.simpleName(className);
    }
}
