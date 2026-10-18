package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderSystems;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;

/**
 * The registry finds the builder systems when first asked: a concurrent caller waits for that discovery instead of
 * seeing them unknown, and a failed discovery is tried again.
 */
class RegistryBuilderLoadingTest {

    private static AgenticSystem system(String id) {
        return new AgenticSystem(id, id, id, SystemOrigin.BUILDER, new SourceRef(id, "m", null), List.of(),
                new AgentNode(id, id, NodeKind.SEQUENCE, null, null, null, false, false, null, null, null, null, null));
    }

    @Test
    void aConcurrentCallerWaitsForTheFirstDiscovery() throws Exception {
        CountDownLatch discovering = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        registry.builderSystems = new BuilderSystems() {
            @Override
            public List<AgenticSystem> discover() {
                discovering.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of(system("built"));
            }

            @Override
            public List<AgenticSystem> built() {
                return List.of();
            }

            @Override
            public List<String> stale() {
                return List.of();
            }
        };
        CompletableFuture<List<AgenticSystem>> first = CompletableFuture.supplyAsync(registry::systems);
        assertTrue(discovering.await(5, TimeUnit.SECONDS));
        CompletableFuture<Optional<AgenticSystem>> second = CompletableFuture.supplyAsync(() -> registry.find("built"));
        Thread.sleep(200); // the second caller arrives while the discovery runs
        release.countDown();

        assertTrue(second.get(5, TimeUnit.SECONDS).isPresent(), "not unknown while being discovered");
        assertEquals(1, first.get(5, TimeUnit.SECONDS).size());
    }

    @Test
    void aFailedDiscoveryIsTriedAgain() {
        int[] calls = { 0 };
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        registry.builderSystems = new BuilderSystems() {
            @Override
            public List<AgenticSystem> discover() {
                if (calls[0]++ == 0) {
                    throw new IllegalStateException("not ready");
                }
                return List.of(system("built"));
            }

            @Override
            public List<AgenticSystem> built() {
                return List.of();
            }

            @Override
            public List<String> stale() {
                return List.of();
            }
        };

        assertEquals(List.of(), registry.systems(), "a failing discovery is logged, the listing goes on");
        assertTrue(registry.find("built").isPresent());
    }
}
