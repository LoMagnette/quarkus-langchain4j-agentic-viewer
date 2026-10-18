package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;

/**
 * Fixture app: a builder system produced by a field, built in the constructor (also of the client proxy): with
 * builder-discovery=transform it is still listed once.
 */
@ApplicationScoped
public class FieldProducer {

    @Produces
    @Named("fieldProduced")
    UntypedAgent fieldProduced = AgenticServices.sequenceBuilder().name("fieldProduced")
            .subAgents(AgenticServices.agentAction(scope -> scope.writeState("produced", true))).build();
}
