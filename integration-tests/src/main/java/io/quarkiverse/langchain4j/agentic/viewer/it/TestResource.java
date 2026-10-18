package io.quarkiverse.langchain4j.agentic.viewer.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ResearchGatherer;

/**
 * Simple test resource to verify the application starts correctly, and runs a declarative system (ProdStartupIT).
 */
@Path("/test")
public class TestResource {

    @Inject
    ResearchGatherer researchGatherer;

    @GET
    @Path("/research")
    @Produces(MediaType.TEXT_PLAIN)
    public String research(@QueryParam("topic") String topic) {
        return researchGatherer.gather(topic);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Agentic Viewer Test";
    }
}
