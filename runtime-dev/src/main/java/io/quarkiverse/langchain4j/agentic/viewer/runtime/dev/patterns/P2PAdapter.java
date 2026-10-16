package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.patterns.p2p.P2PPlanner;
import dev.langchain4j.agentic.planner.Planner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * Peer-to-peer planners of langchain4j-agentic-patterns. Registered only when the module is present.
 */
@ApplicationScoped
public class P2PAdapter implements PatternAdapter {

    @Override
    public PatternType patternType() {
        return PatternType.P2P;
    }

    @Override
    public boolean handles(Planner planner) {
        return planner instanceof P2PPlanner;
    }
}
