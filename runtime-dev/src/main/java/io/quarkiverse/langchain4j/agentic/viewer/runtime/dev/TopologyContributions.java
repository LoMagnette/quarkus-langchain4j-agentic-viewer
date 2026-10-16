package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.AgenticTopologyContributor;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.ContributedEdge;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.ContributedNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.TopologyContribution;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;

/**
 * Applies the application's {@link AgenticTopologyContributor}s (found at build time) to the planner nodes they
 * describe. A contribution is returned with resolved references: edges between node ids, contributed nodes with the id
 * {@code <planner node id>::<id>}. A contributor that cannot be created or fails is logged and skipped; so are edges
 * whose ends are unknown.
 */
public class TopologyContributions {

    private static final Logger LOG = Logger.getLogger(TopologyContributions.class);
    static final String SEPARATOR = "::";

    private final List<AgenticTopologyContributor> contributors;

    public TopologyContributions(List<AgenticTopologyContributor> contributors) {
        this.contributors = List.copyOf(contributors);
    }

    /**
     * Creates the contributors from their class names with their no-argument constructor, through the context class
     * loader (the application's).
     */
    public static TopologyContributions of(List<String> classNames) {
        List<AgenticTopologyContributor> contributors = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className, true, classLoader);
                contributors.add((AgenticTopologyContributor) type.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warnf(e, "Agentic viewer: cannot create the topology contributor %s (a public no-argument constructor"
                        + " is needed), it is ignored", className);
            }
        }
        return new TopologyContributions(contributors);
    }

    public boolean isEmpty() {
        return contributors.isEmpty();
    }

    /**
     * @return the resolved contributions of a system's planner nodes, by node id
     */
    public Map<String, TopologyContribution> of(AgenticSystem system) {
        Map<String, TopologyContribution> contributions = new LinkedHashMap<>();
        if (!contributors.isEmpty()) {
            visit(system.root(), contributions);
        }
        return contributions;
    }

    private void visit(AgentNode node, Map<String, TopologyContribution> contributions) {
        String plannerAgent = plannerAgent(node);
        if (plannerAgent != null) {
            for (AgenticTopologyContributor contributor : contributors) {
                if (describes(contributor, plannerAgent)) {
                    contribution(contributor, node).ifPresent(c -> contributions.merge(node.id(), c,
                            (a, b) -> new TopologyContribution(concat(a.nodes(), b.nodes()), concat(a.edges(), b.edges()))));
                }
            }
        }
        node.children().forEach(child -> visit(child, contributions));
    }

    /** The class declaring the {@code @PlannerSupplier} of a planner node, null for any other node. */
    private static String plannerAgent(AgentNode node) {
        if (node.kind() != NodeKind.PLANNER || node.plannerInfo() == null) {
            return null;
        }
        String supplier = node.plannerInfo().extras().get("supplier");
        return supplier == null ? null : supplier.substring(0, supplier.indexOf('#') < 0 ? supplier.length() : supplier.indexOf('#'));
    }

    private static boolean describes(AgenticTopologyContributor contributor, String plannerAgent) {
        try {
            Class<?> type = contributor.plannerAgent();
            return type != null && type.getName().equals(plannerAgent);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Agentic viewer: topology contributor %s failed", contributor.getClass().getName());
            return false;
        }
    }

    private static Optional<TopologyContribution> contribution(AgenticTopologyContributor contributor,
            AgentNode plannerNode) {
        TopologyContribution raw;
        try {
            raw = contributor.contribute(plannerNode);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Agentic viewer: topology contributor %s failed on %s", contributor.getClass().getName(),
                    plannerNode.id());
            return Optional.empty();
        }
        if (raw == null) {
            return Optional.empty();
        }
        // references: contributed node ids, then sub-agent node ids, then sub-agent names
        Map<String, String> ids = new HashMap<>();
        plannerNode.children().forEach(child -> ids.putIfAbsent(child.name(), child.id()));
        plannerNode.children().forEach(child -> ids.put(child.id(), child.id()));
        List<ContributedNode> nodes = new ArrayList<>();
        for (ContributedNode node : raw.nodes()) {
            if (node == null || node.id() == null || node.id().isBlank()) {
                continue;
            }
            String id = plannerNode.id() + SEPARATOR + node.id();
            ids.put(node.id(), id);
            nodes.add(new ContributedNode(id, node.label() == null ? node.id() : node.label()));
        }
        List<ContributedEdge> edges = new ArrayList<>();
        for (ContributedEdge edge : raw.edges()) {
            String source = edge == null ? null : ids.get(edge.source());
            String target = edge == null ? null : ids.get(edge.target());
            if (source == null || target == null) {
                LOG.warnf("Agentic viewer: topology contributor %s draws an edge %s on %s whose ends are not sub-agents or"
                        + " contributed nodes, it is ignored", contributor.getClass().getName(), edge, plannerNode.id());
                continue;
            }
            edges.add(new ContributedEdge(source, target, edge.label()));
        }
        return Optional.of(new TopologyContribution(nodes, edges));
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
