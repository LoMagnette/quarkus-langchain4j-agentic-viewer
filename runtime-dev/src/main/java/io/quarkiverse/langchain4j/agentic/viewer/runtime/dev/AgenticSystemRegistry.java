package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

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

    @PostConstruct
    void loadBuildTimeSystems() {
        AgenticViewerRecorder.getSystems().forEach(this::register);
    }

    /**
     * Adds a system, or replaces the one with the same id.
     */
    public void register(AgenticSystem system) {
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
