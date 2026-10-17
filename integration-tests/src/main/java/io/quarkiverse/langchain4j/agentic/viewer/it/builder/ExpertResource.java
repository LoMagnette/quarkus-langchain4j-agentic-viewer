package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ExpertRouter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ExpertRouter.RequestCategory;

/**
 * Builder system rebuilt on every request: the experts of {@link ExpertRouter} behind lambda conditions, one with a
 * description and one without.
 */
@Path("/builder/expert")
public class ExpertResource {

    @Inject
    ExpertRouter.CategoryRouter categoryRouter;

    @Inject
    ExpertRouter.MedicalExpert medicalExpert;

    @Inject
    ExpertRouter.TechnicalExpert technicalExpert;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String ask(@QueryParam("request") String request) {
        UntypedAgent experts = AgenticServices.conditionalBuilder()
                .name("askExpert")
                .description("Asks the expert matching the category")
                .subAgents("category is medical", scope -> category(scope.readState("category")) == RequestCategory.MEDICAL,
                        AgentBeans.unwrap(medicalExpert))
                .subAgents(scope -> category(scope.readState("category")) == RequestCategory.TECHNICAL,
                        AgentBeans.unwrap(technicalExpert))
                .outputKey("response")
                .build();
        UntypedAgent router = AgenticServices.sequenceBuilder()
                .name("ask")
                .description("Routes a request to the right expert, rebuilt per request")
                .subAgents(AgentBeans.unwrap(categoryRouter, experts))
                .outputKey("response")
                .build();
        return String.valueOf(router.invoke(Map.of("request", request)));
    }

    private static RequestCategory category(Object value) {
        return value instanceof RequestCategory category ? category
                : value == null ? RequestCategory.UNKNOWN : RequestCategory.valueOf(value.toString().trim());
    }
}
