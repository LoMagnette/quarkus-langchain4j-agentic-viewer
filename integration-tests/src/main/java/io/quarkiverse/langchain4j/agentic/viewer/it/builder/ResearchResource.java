package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Uses the {@link ResearchService}, whose system is built with the service, on its first use.
 */
@Path("/builder/research")
public class ResearchResource {

    @Inject
    ResearchService researchService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String research(@QueryParam("topic") String topic) {
        return researchService.research(topic);
    }
}
