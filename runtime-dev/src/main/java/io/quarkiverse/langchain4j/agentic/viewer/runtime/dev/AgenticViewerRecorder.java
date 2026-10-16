package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.List;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Hands what the build knows to the dev-mode runtime.
 */
@Recorder
public class AgenticViewerRecorder {

    private static volatile List<AgenticSystem> systems = List.of();

    /**
     * Sets the agentic systems known at build time (declarative ones).
     */
    public void setSystems(List<AgenticSystem> buildTimeSystems) {
        systems = List.copyOf(buildTimeSystems);
    }

    /**
     * @return the agentic systems known at build time, empty before static init
     */
    public static List<AgenticSystem> getSystems() {
        return systems;
    }
}
