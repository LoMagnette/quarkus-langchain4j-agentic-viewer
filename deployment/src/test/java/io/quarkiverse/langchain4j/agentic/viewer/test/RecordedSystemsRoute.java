package io.quarkiverse.langchain4j.agentic.viewer.test;

import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerRecorder;
import io.vertx.ext.web.Router;

/**
 * Test app route exposing what runtime-dev received from the recorder: one line per system, with its node ids.
 */
@ApplicationScoped
public class RecordedSystemsRoute {

    void register(@Observes Router router) {
        router.get("/recorded-systems").handler(rc -> rc.end(AgenticViewerRecorder.getSystems().stream()
                .map(system -> system.id() + " " + system.root().children().stream()
                        .map(child -> child.id().substring(system.id().length()))
                        .toList())
                .collect(Collectors.joining("\n"))));
    }
}
