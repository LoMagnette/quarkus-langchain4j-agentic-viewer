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

    /**
     * How the systems built with the {@code AgenticServices} builders are found, in dev mode. {@code cdi}: the beans
     * the application produces whose type is an agent ({@code UntypedAgent}, {@code SupervisorAgent}) or whose producer
     * calls a builder's {@code build()}; they are created when the Dev UI first lists the systems, not at startup.
     * Systems kept in a field or built per call are not found. {@code transform}: also every system the application
     * builds, beans or not, once built: in dev mode only, the application classes calling a builder's {@code build()}
     * are rewritten to report its result (one entry per call site, its latest instance). {@code none}: declarative
     * systems only.
     */
    @WithDefault("cdi")
    BuilderDiscovery builderDiscovery();

    enum BuilderDiscovery {
        /** Builder systems produced as CDI beans. */
        CDI,
        /** CDI, plus every system built by the application, found by a dev-mode bytecode transform. */
        TRANSFORM,
        /** No builder system. */
        NONE
    }
}
