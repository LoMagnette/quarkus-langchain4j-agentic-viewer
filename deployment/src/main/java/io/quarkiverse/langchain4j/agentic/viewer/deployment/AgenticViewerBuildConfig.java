package io.quarkiverse.langchain4j.agentic.viewer.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * The Agentic Systems Dev UI.
 */
@ConfigMapping(prefix = "quarkus.langchain4j.agentic.dev-ui")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface AgenticViewerBuildConfig {

    /**
     * Whether the Agentic Systems Dev UI is enabled in dev mode: its page and JSON-RPC service, and the listener and
     * planner decorators it attaches to agentic systems for runs. Nothing of it is ever part of a production build.
     */
    @WithDefault("true")
    boolean enabled();
}
