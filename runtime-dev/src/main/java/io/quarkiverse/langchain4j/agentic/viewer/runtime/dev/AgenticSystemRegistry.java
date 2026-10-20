package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderSystems;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.TopologyContribution;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;

/**
 * The agentic systems of the running application, by id: the declarative ones known at build time, and later the
 * builder ones discovered at runtime (Phase 5), so the Dev UI sees both the same way.
 */
@ApplicationScoped
public class AgenticSystemRegistry {

    private static final Logger LOG = Logger.getLogger(AgenticSystemRegistry.class);

    // by id, in registration order: an immutable snapshot replaced as a whole (under mutation), so a reader never sees
    // an id without its system
    private volatile Map<String, AgenticSystem> systems = Map.of();
    private final Object mutation = new Object();

    // builder systems (T5.1): found when first asked for, so their beans are not created at startup
    @Inject
    BuilderSystems builderSystems;
    private volatile boolean builderSystemsLoaded;
    private final List<String> buildTimeIds = new CopyOnWriteArrayList<>();

    private volatile TopologyContributions contributions = new TopologyContributions(List.of());
    // per system id, for the registered instance it was computed for (a replaced system gets new ones)
    private final Map<String, Contributions> contributionsBySystem = new ConcurrentHashMap<>();

    private record Contributions(AgenticSystem system, Map<String, TopologyContribution> byNodeId) {
    }

    @PostConstruct
    void loadBuildTimeSystems() {
        AgenticViewerRecorder.getSystems().forEach(system -> {
            register(system);
            buildTimeIds.add(system.id());
        });
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
        return find(systemId).map(system -> contributionsBySystem.compute(system.id(),
                (id, known) -> known != null && known.system() == system ? known
                        : new Contributions(system, contributions.of(system)))
                .byNodeId()).orElse(Map.of());
    }

    /**
     * Adds a system, or replaces the one with the same id.
     */
    public void register(AgenticSystem system) {
        synchronized (mutation) {
            if (systems.get(system.id()) == system) {
                return; // the same instance again (built systems are handed over on each listing)
            }
            Map<String, AgenticSystem> next = new LinkedHashMap<>(systems);
            next.put(system.id(), system); // a replaced system keeps its place
            systems = Collections.unmodifiableMap(next);
        }
    }

    /** Removes a system (a builder system that became part of another). */
    void unregister(String systemId) {
        synchronized (mutation) {
            if (systems.containsKey(systemId)) {
                Map<String, AgenticSystem> next = new LinkedHashMap<>(systems);
                next.remove(systemId);
                systems = Collections.unmodifiableMap(next);
            }
        }
        contributionsBySystem.remove(systemId);
    }

    /**
     * @return the systems in registration order, builder systems found at the first call
     */
    public List<AgenticSystem> systems() {
        loadBuilderSystems();
        return List.copyOf(systems.values());
    }

    /**
     * @return the systems known at build time (declarative) still registered, without creating any builder system bean
     */
    public List<AgenticSystem> buildTimeSystems() {
        Map<String, AgenticSystem> current = systems;
        return buildTimeIds.stream().map(current::get).filter(Objects::nonNull).toList();
    }

    public Optional<AgenticSystem> find(String systemId) {
        loadBuilderSystems();
        return Optional.ofNullable(systemId).map(systems::get);
    }

    private void loadBuilderSystems() {
        if (builderSystems == null) {
            return;
        }
        if (!builderSystemsLoaded) {
            // a concurrent caller waits here for the discovery, instead of seeing the builder systems unknown
            synchronized (this) {
                if (!builderSystemsLoaded) {
                    try {
                        builderSystems.discover().forEach(this::register);
                        builderSystemsLoaded = true;
                    } catch (RuntimeException e) {
                        LOG.warnf(e, "Agentic viewer: cannot discover the builder systems, tried again on the next call");
                    }
                }
            }
        }
        try {
            // systems built since (builder-discovery=transform): new call sites, or newer instances at known ones
            builderSystems.built().forEach(this::register);
            builderSystems.stale().forEach(this::unregister);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Agentic viewer: cannot list the systems built by the application");
        }
    }
}
