package io.quarkiverse.langchain4j.agentic.viewer.test.ui;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.vertx.ext.web.Router;

/**
 * Fixture app of the UI tests: the application calling a system itself, {@code GET /newsletter?topic=...}, as a REST
 * endpoint would (an application run, not started from the Dev UI).
 */
@ApplicationScoped
public class ApplicationCall {

    @Inject
    NewsletterWorkflow workflow;

    void route(@Observes Router router) {
        router.get("/newsletter").blockingHandler(context -> context.response()
                .end(workflow.write(context.request().getParam("topic"), List.of("almanac"))));
    }
}
