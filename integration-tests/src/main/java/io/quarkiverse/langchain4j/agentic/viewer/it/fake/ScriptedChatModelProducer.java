package io.quarkiverse.langchain4j.agentic.viewer.it.fake;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import io.quarkus.arc.profile.UnlessBuildProfile;

/**
 * The application's only chat model: a {@link ScriptedChatModel} assembled from every {@link ChatModelScript} bean.
 * No langchain4j provider extension is used, so the samples and tests never need an API key.
 * <p>
 * Not produced with the {@code ollama} profile: the samples then run on a real model (application.properties).
 */
@ApplicationScoped
public class ScriptedChatModelProducer {

    @Produces
    @ApplicationScoped
    @UnlessBuildProfile("ollama")
    ScriptedChatModel chatModel(Instance<ChatModelScript> scripts) {
        ScriptedChatModel.Builder builder = ScriptedChatModel.builder();
        scripts.forEach(script -> script.contribute(builder));
        return builder.build();
    }
}
