package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;
import io.quarkus.arc.ClientProxy;

/**
 * Fixture app: builder systems produced as beans, made of non-AI agents (no model needed). The producers log when
 * they run, so a test can tell when the beans are created.
 */
@ApplicationScoped
public class PipelineSystems {

    static final Logger LOG = Logger.getLogger(PipelineSystems.class);

    /** A typed agent interface without agentic annotation: only its producer tells it is an agentic system. */
    public interface Greeter {
        String greet(@V("name") String name);
    }

    /** A sub-agent of the pipeline, produced as a bean of its own: not a root. */
    @Produces
    @ApplicationScoped
    @Named("cleanup")
    UntypedAgent cleanup() {
        LOG.info("producing cleanup");
        return AgenticServices.loopBuilder().name("cleanup")
                .subAgents(AgenticServices.agentAction(scope -> scope.writeState("clean", true)))
                .maxIterations(2).build();
    }

    @Produces
    @ApplicationScoped
    @Named("pipeline")
    UntypedAgent pipeline(@Named("cleanup") UntypedAgent cleanup) {
        LOG.info("producing pipeline");
        return AgenticServices.sequenceBuilder().name("pipeline").description("Prepares, then cleans up")
                .subAgents(AgenticServices.agentAction(scope -> scope.writeState("prepared", true)),
                        ClientProxy.unwrap(cleanup))
                .build();
    }

    @Produces
    @ApplicationScoped
    Greeter greeter() {
        LOG.info("producing greeter");
        return AgenticServices.sequenceBuilder(Greeter.class).name("greet")
                .subAgents(AgenticServices.agentAction(scope -> scope.writeState("greeting", "hello"))).build();
    }

    /** The build wants a chat model even though no agent here calls one. */
    @ApplicationScoped
    public static class FixtureChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("fixture")).build();
        }
    }
}
