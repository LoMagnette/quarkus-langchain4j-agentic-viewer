package io.quarkiverse.langchain4j.agentic.viewer.deployment;

import java.util.function.BooleanSupplier;

/**
 * Whether builder systems are discovered among the CDI beans ({@code builder-discovery=cdi}, the default).
 */
public class BuilderDiscoveryCdi implements BooleanSupplier {

    private final AgenticViewerBuildConfig config;

    BuilderDiscoveryCdi(AgenticViewerBuildConfig config) {
        this.config = config;
    }

    @Override
    public boolean getAsBoolean() {
        return config.builderDiscovery() == AgenticViewerBuildConfig.BuilderDiscovery.CDI;
    }
}
