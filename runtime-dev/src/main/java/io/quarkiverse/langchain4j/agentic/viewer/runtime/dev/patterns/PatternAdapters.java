package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.planner.Planner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * The pattern adapters of the application: none without langchain4j-agentic-patterns.
 */
@ApplicationScoped
public class PatternAdapters {

    private final List<PatternAdapter> adapters;

    @Inject
    public PatternAdapters(@Any Instance<PatternAdapter> adapters) {
        this(adapters.stream().toList());
    }

    public PatternAdapters(List<PatternAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    /** No adapter: every planner gets the generic view. */
    public static PatternAdapters none() {
        return new PatternAdapters(List.of());
    }

    public List<PatternAdapter> adapters() {
        return adapters;
    }

    /**
     * @return the adapter of a pattern, empty without it (e.g. without langchain4j-agentic-patterns)
     */
    public Optional<PatternAdapter> adapterFor(PatternType pattern) {
        return adapters.stream().filter(adapter -> adapter.patternType() == pattern).findFirst();
    }

    /**
     * @return the adapter of a planner, empty for a planner of no known pattern
     */
    public Optional<PatternAdapter> adapterFor(Planner planner) {
        return planner == null ? Optional.empty() : adapters.stream().filter(adapter -> adapter.handles(planner)).findFirst();
    }
}
