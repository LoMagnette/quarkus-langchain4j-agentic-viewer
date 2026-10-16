package io.quarkiverse.langchain4j.agentic.viewer.deployment;

import java.util.function.BooleanSupplier;

/**
 * Build steps of the Agentic Systems Dev UI run only when it is enabled ({@code quarkus.langchain4j.agentic.dev-ui.enabled}).
 */
public class AgenticViewerEnabled implements BooleanSupplier {

    private final AgenticViewerBuildConfig config;

    AgenticViewerEnabled(AgenticViewerBuildConfig config) {
        this.config = config;
    }

    @Override
    public boolean getAsBoolean() {
        return config.enabled();
    }
}
