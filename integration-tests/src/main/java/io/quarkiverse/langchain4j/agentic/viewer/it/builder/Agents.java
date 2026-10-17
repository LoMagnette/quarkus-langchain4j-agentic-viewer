package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.arc.ClientProxy;

/**
 * Sub-agents of the builder samples. A langchain4j agent has one parent, and building a system makes it the parent of
 * its sub-agents (and renames them): the agent beans Quarkus creates for agent interfaces are the ones its declarative
 * systems use, so a builder must not take them. Each builder system gets its own agents, built from the annotated
 * interfaces with the application's chat model (given the interface alone, the builder would have no model).
 */
final class Agents {

    private Agents() {
    }

    /** A new agent of an annotated agent interface. */
    static Object fresh(ChatModel chatModel, Class<?> agentInterface) {
        return AgenticServices.agentBuilder(agentInterface).chatModel(chatModel).build();
    }

    /** A builder system produced as a bean, used by one other system only: the instance behind its client proxy. */
    static Object unwrap(Object agent) {
        return agent instanceof ClientProxy ? ClientProxy.unwrap(agent) : agent;
    }
}
