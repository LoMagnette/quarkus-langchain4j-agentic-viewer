package io.quarkiverse.langchain4j.agentic.viewer.it.fake;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

/**
 * The application's only chat model: a {@link ScriptedChatModel} assembled from every {@link ChatModelScript} bean.
 * No langchain4j provider extension is used, so the samples and tests never need an API key.
 */
@ApplicationScoped
public class ScriptedChatModelProducer {

    @Produces
    @ApplicationScoped
    ScriptedChatModel chatModel(Instance<ChatModelScript> scripts) {
        ScriptedChatModel.Builder builder = ScriptedChatModel.builder();
        scripts.forEach(script -> script.contribute(builder));
        return builder.build();
    }
}
