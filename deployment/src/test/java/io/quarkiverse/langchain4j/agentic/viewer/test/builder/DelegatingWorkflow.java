package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkus.arc.Unremovable;

/**
 * Fixture app: a declarative workflow whose step (a non-AI agent bean) runs the builder system "pipeline": MIXED.
 */
public interface DelegatingWorkflow {

    @SequenceAgent(outputKey = "done", subAgents = { Step.class })
    String run(@V("topic") String topic);

    @ApplicationScoped
    @Unremovable
    class Step {

        @Inject
        @Named("pipeline")
        UntypedAgent pipeline;

        @Agent(description = "Runs the pipeline", outputKey = "done")
        public String step(@V("topic") String topic) {
            pipeline.invoke(Map.of("topic", topic));
            return "done";
        }
    }

    @ApplicationScoped
    class EntryPoint {
        @Inject
        DelegatingWorkflow workflow;
    }
}
