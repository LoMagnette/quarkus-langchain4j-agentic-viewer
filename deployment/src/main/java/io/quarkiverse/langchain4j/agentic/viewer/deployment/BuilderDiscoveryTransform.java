package io.quarkiverse.langchain4j.agentic.viewer.deployment;

import java.util.function.BooleanSupplier;

/**
 * Whether the application classes calling a builder's {@code build()} are transformed ({@code builder-discovery=transform}).
 */
public class BuilderDiscoveryTransform implements BooleanSupplier {

    private final AgenticViewerBuildConfig config;

    BuilderDiscoveryTransform(AgenticViewerBuildConfig config) {
        this.config = config;
    }

    @Override
    public boolean getAsBoolean() {
        return config.builderDiscovery() == AgenticViewerBuildConfig.BuilderDiscovery.TRANSFORM;
    }
}
