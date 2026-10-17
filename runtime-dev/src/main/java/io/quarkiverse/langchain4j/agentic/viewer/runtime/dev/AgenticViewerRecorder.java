package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.List;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderCandidate;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Hands what the build knows to the dev-mode runtime.
 */
@Recorder
public class AgenticViewerRecorder {

    private static volatile List<AgenticSystem> systems = List.of();
    private static volatile List<String> topologyContributors = List.of();
    private static volatile List<BuilderCandidate> builderCandidates = List.of();

    /**
     * Sets the agentic systems known at build time (declarative ones).
     */
    public void setSystems(List<AgenticSystem> buildTimeSystems) {
        systems = List.copyOf(buildTimeSystems);
    }

    /**
     * Sets the class names of the application's AgenticTopologyContributor implementations.
     */
    public void setTopologyContributors(List<String> classNames) {
        topologyContributors = List.copyOf(classNames);
    }

    /**
     * Sets the beans that may be builder systems (builder-discovery=cdi).
     */
    public void setBuilderCandidates(List<BuilderCandidate> candidates) {
        builderCandidates = List.copyOf(candidates);
    }

    /**
     * @return the beans that may be builder systems, created when the Dev UI asks
     */
    public static List<BuilderCandidate> getBuilderCandidates() {
        return builderCandidates;
    }

    /**
     * @return the class names of the application's AgenticTopologyContributor implementations
     */
    public static List<String> getTopologyContributors() {
        return topologyContributors;
    }

    /**
     * @return the agentic systems known at build time, empty before static init
     */
    public static List<AgenticSystem> getSystems() {
        return systems;
    }
}
