package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.TopologyContribution;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;

/**
 * The agentic systems of the running application, by id: the declarative ones known at build time, and later the
 * builder ones discovered at runtime (Phase 5), so the Dev UI sees both the same way.
 */
@ApplicationScoped
public class AgenticSystemRegistry {

    // registration order is kept for listing; ids are unique
    private final Map<String, AgenticSystem> systems = new ConcurrentHashMap<>();
    private final List<String> order = new CopyOnWriteArrayList<>();

    private volatile TopologyContributions contributions = new TopologyContributions(List.of());
    private final Map<String, Map<String, TopologyContribution>> contributionsBySystem = new ConcurrentHashMap<>();

    @PostConstruct
    void loadBuildTimeSystems() {
        AgenticViewerRecorder.getSystems().forEach(this::register);
        useContributions(TopologyContributions.of(AgenticViewerRecorder.getTopologyContributors()));
    }

    /**
     * Sets the topology contributors applied to the planner nodes of the systems.
     */
    public void useContributions(TopologyContributions topologyContributions) {
        contributions = topologyContributions;
        contributionsBySystem.clear();
    }

    /**
     * @return what the application's topology contributors draw in the planner nodes of a system, by node id
     */
    public Map<String, TopologyContribution> contributions(String systemId) {
        return find(systemId).map(system -> contributionsBySystem.computeIfAbsent(system.id(),
                id -> contributions.of(system))).orElse(Map.of());
    }

    /**
     * Adds a system, or replaces the one with the same id.
     */
    public void register(AgenticSystem system) {
        contributionsBySystem.remove(system.id());
        if (systems.put(system.id(), system) == null) {
            order.add(system.id());
        }
    }

    /**
     * @return the systems in registration order
     */
    public List<AgenticSystem> systems() {
        return order.stream().map(systems::get).toList();
    }

    public Optional<AgenticSystem> find(String systemId) {
        return Optional.ofNullable(systemId).map(systems::get);
    }
}
