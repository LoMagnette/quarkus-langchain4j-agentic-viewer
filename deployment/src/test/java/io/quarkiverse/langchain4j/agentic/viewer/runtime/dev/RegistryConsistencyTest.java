package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;

/**
 * The registry is read while builder systems come and go: a listing is one consistent snapshot, in registration order.
 */
class RegistryConsistencyTest {

    private static AgenticSystem system(String id) {
        return new AgenticSystem(id, id, id, SystemOrigin.BUILDER, new SourceRef(id, "m", null), List.of(),
                new AgentNode(id, id, NodeKind.SEQUENCE, null, null, null, false, false, null, null, null, null, null));
    }

    @Test
    void aReplacedSystemKeepsItsPlace() {
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        registry.register(system("a"));
        registry.register(system("b"));
        registry.register(system("c"));
        AgenticSystem newerB = system("b");
        registry.register(newerB);
        registry.unregister("a");
        registry.register(system("a"));

        assertEquals(List.of("b", "c", "a"), registry.systems().stream().map(AgenticSystem::id).toList());
        assertSame(newerB, registry.find("b").orElseThrow());
    }

    @Test
    void aListingNeverSeesAHalfRegisteredSystem() throws Exception {
        AgenticSystemRegistry registry = new AgenticSystemRegistry();
        registry.register(system("first"));
        registry.register(system("last"));
        AtomicBoolean stop = new AtomicBoolean();
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 20_000; i++) {
                registry.register(system("built-" + (i % 5)));
                registry.register(system("last")); // replaced in place
                registry.unregister("built-" + ((i + 2) % 5));
            }
            stop.set(true);
        });
        int listings = 0;
        while (!stop.get()) {
            List<AgenticSystem> listed = registry.systems();
            for (AgenticSystem system : listed) {
                assertNotNull(system, () -> "null entry in " + listed);
            }
            List<String> ids = listed.stream().map(AgenticSystem::id).toList();
            assertEquals(ids.size(), ids.stream().distinct().count(), () -> "duplicate ids: " + ids);
            assertEquals(List.of("first", "last"), ids.stream().filter(id -> !id.startsWith("built-")).toList());
            listings++;
        }
        writer.get(10, TimeUnit.SECONDS);
        assertTrue(listings > 0);
    }
}
