package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The checks of a system's wiring, the same for declarative systems (at build time) and builder systems (at runtime):
 * <ul>
 * <li>warning: a conditional sub-agent without activation condition (langchain4j leaves it out);</li>
 * <li>warning: a loop without exit condition and more iterations than a limit (it always runs them all);</li>
 * <li>hint: an agent's output key no agent, condition or {@code @Output} of the system reads (when the reads are
 * known);</li>
 * <li>hint: a key an agent reads that no agent of the system writes and that is no input of the system.</li>
 * </ul>
 * The hints are silenced under supervisors, planners and parallel mappers, whose agents get their arguments at runtime.
 * A non-AI agent without output key may write the scope in code (scope.writeState, an agentAction): in a system with
 * one, the "never written" hint is left out; other keys written in code may still make a hint wrong: they are hints.
 */
public final class TopologyChecks {

    /** Iterations above which a loop without exit condition is reported, by default. */
    public static final int DEFAULT_LOOP_ITERATIONS = 5;

    private static final Set<NodeKind> DYNAMIC = Set.of(NodeKind.SUPERVISOR, NodeKind.PLANNER, NodeKind.PARALLEL_MAPPER);

    private TopologyChecks() {
    }

    /**
     * @param droppedBranches by conditional node id, the sub-agent class names without activation condition (known
     *        at build time only)
     * @param extraReads keys read by conditions and {@code @Output} methods, null when unknown (lambdas of builder
     *        systems): no "never read" hint then
     * @param loopIterations iterations above which a loop without exit condition is reported
     */
    public static List<TopologyWarning> check(AgentNode root, Map<String, List<String>> droppedBranches,
            Collection<String> extraReads, int loopIterations) {
        List<TopologyWarning> warnings = new ArrayList<>();
        Set<String> written = new HashSet<>();
        Set<String> read = new HashSet<>(extraReads == null ? List.of() : extraReads);
        List<Map.Entry<AgentNode, Boolean>> nodes = new ArrayList<>();
        collect(root, false, written, read, nodes);
        Set<String> inputs = Set.copyOf(root.inputKeys());
        boolean writesKnown = nodes.stream().map(Map.Entry::getKey)
                .noneMatch(node -> node.kind() == NodeKind.NON_AI_AGENT && node.outputKey() == null);
        // what a workflow returns is read by its caller: the root's output and every workflow's output key
        Set<String> returned = new HashSet<>();
        nodes.stream().map(Map.Entry::getKey).filter(node -> !node.children().isEmpty() && node.outputKey() != null)
                .forEach(node -> returned.add(node.outputKey()));
        if (root.outputKey() != null) {
            returned.add(root.outputKey());
        }
        nodes.forEach(entry -> {
            AgentNode node = entry.getKey();
            boolean underDynamic = entry.getValue();
            droppedBranches.getOrDefault(node.id(), List.of()).forEach(subAgent -> warnings.add(new TopologyWarning(
                    node.id(), TopologyWarning.Level.WARNING, "branch-without-condition",
                    NodeIds.simpleName(subAgent) + " has no @ActivationCondition: langchain4j leaves it out of the "
                            + "conditional, it never runs")));
            if (node.kind() == NodeKind.LOOP && node.guard() != null && node.guard().label() == null
                    && node.guard().maxIterations() != null && node.guard().maxIterations() > loopIterations) {
                warnings.add(new TopologyWarning(node.id(), TopologyWarning.Level.WARNING, "loop-without-exit",
                        "No exit condition: the loop always runs its " + node.guard().maxIterations() + " iterations"));
            }
            if (underDynamic) {
                return;
            }
            if (extraReads != null && node.outputKey() != null && !read.contains(node.outputKey())
                    && !returned.contains(node.outputKey())) {
                warnings.add(new TopologyWarning(node.id(), TopologyWarning.Level.HINT, "output-never-read",
                        "Nothing in the system reads its output key '" + node.outputKey() + "'"));
            }
            for (String key : writesKnown ? node.inputKeys() : List.<String> of()) {
                if (!written.contains(key) && !inputs.contains(key)) {
                    warnings.add(new TopologyWarning(node.id(), TopologyWarning.Level.HINT, "key-never-written",
                            "No agent of the system writes '" + key + "' and it is no input of the system (unless code "
                                    + "writes it)"));
                }
            }
        });
        return warnings;
    }

    /** Keys written and read by the agents, and each node with whether a dynamic workflow is above it. */
    private static void collect(AgentNode node, boolean underDynamic, Set<String> written, Set<String> read,
            List<Map.Entry<AgentNode, Boolean>> nodes) {
        nodes.add(Map.entry(node, underDynamic));
        if (node.outputKey() != null) {
            written.add(node.outputKey());
        }
        if (!node.children().isEmpty()) {
            // a workflow reads nothing itself: its inputs are what its agents read (a planner reads them all)
            if (DYNAMIC.contains(node.kind())) {
                read.addAll(node.inputKeys());
            }
        } else {
            read.addAll(node.inputKeys());
        }
        boolean dynamicBelow = underDynamic || DYNAMIC.contains(node.kind());
        node.children().forEach(child -> collect(child, dynamicBelow, written, read, nodes));
    }
}
