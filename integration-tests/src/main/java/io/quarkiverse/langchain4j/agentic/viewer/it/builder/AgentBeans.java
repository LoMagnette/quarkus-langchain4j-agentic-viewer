package io.quarkiverse.langchain4j.agentic.viewer.it.builder;

import io.quarkus.arc.ClientProxy;

/**
 * Agent beans as builder sub-agents. Quarkus creates a bean per agent interface, with the application's chat model; a
 * builder given the interface itself would build the agent anew, without it. Normal-scoped beans are client proxies,
 * which langchain4j does not recognise as agents: the builder needs the instance behind.
 */
final class AgentBeans {

    private AgentBeans() {
    }

    static Object[] unwrap(Object... agents) {
        Object[] instances = new Object[agents.length];
        for (int i = 0; i < agents.length; i++) {
            instances[i] = agents[i] instanceof ClientProxy ? ClientProxy.unwrap(agents[i]) : agents[i];
        }
        return instances;
    }
}
