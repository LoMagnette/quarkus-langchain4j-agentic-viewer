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
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ExpertRouter;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.ExpertRouter.RequestCategory;

/**
 * Builder system rebuilt on every request: the experts of {@link ExpertRouter} behind lambda conditions, one with a
 * description and one without.
 */
@Path("/builder/expert")
public class ExpertResource {

    @Inject
    ChatModel chatModel;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String ask(@QueryParam("request") String request) {
        UntypedAgent experts = AgenticServices.conditionalBuilder()
                .name("askExpert")
                .description("Asks the expert matching the category")
                .subAgents("category is medical", scope -> category(scope.readState("category")) == RequestCategory.MEDICAL,
                        Agents.fresh(chatModel, ExpertRouter.MedicalExpert.class))
                .subAgents(scope -> category(scope.readState("category")) == RequestCategory.TECHNICAL,
                        Agents.fresh(chatModel, ExpertRouter.TechnicalExpert.class))
                .outputKey("response")
                .build();
        UntypedAgent router = AgenticServices.sequenceBuilder()
                .name("ask")
                .description("Routes a request to the right expert, rebuilt per request")
                .subAgents(Agents.fresh(chatModel, ExpertRouter.CategoryRouter.class), experts)
                .outputKey("response")
                .build();
        return String.valueOf(router.invoke(Map.of("request", request)));
    }

    private static RequestCategory category(Object value) {
        return value instanceof RequestCategory category ? category
                : value == null ? RequestCategory.UNKNOWN : RequestCategory.valueOf(value.toString().trim());
    }
}
