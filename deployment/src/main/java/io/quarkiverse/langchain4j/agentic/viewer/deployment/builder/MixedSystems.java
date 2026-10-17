package io.quarkiverse.langchain4j.agentic.viewer.deployment.builder;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.quarkus.arc.deployment.SynthesisFinishedBuildItem;
import io.quarkus.arc.processor.BeanResolver;
import io.quarkus.arc.processor.InjectionPointInfo;

/**
 * The declarative systems using a builder system (T5.5b): one of their agents is a bean (a non-AI agent class)
 * injecting a bean that is a builder system. They are tagged MIXED.
 */
public final class MixedSystems {

    private MixedSystems() {
    }

    /**
     * @param agentClasses agent class by node id (TopologyBuilder.agentClasses)
     * @return the ids of the declarative systems using a builder system
     */
    public static Set<String> declarativeUsingBuilders(Map<String, String> agentClasses, SynthesisFinishedBuildItem beans,
            BuilderSystemBeans builderSystems) {
        Set<String> mixed = new TreeSet<>();
        BeanResolver resolver = beans.getBeanResolver();
        agentClasses.forEach((nodeId, agentClass) -> {
            boolean usesBuilder = beans.beanStream().classBeans().filter(bean -> bean.getBeanClass().toString().equals(agentClass))
                    .stream().flatMap(bean -> bean.getAllInjectionPoints().stream())
                    .anyMatch(injection -> injectsBuilderSystem(injection, resolver, builderSystems));
            if (usesBuilder) {
                mixed.add(systemId(nodeId));
            }
        });
        return mixed;
    }

    private static boolean injectsBuilderSystem(InjectionPointInfo injection, BeanResolver resolver,
            BuilderSystemBeans builderSystems) {
        return resolver.resolveBeans(injection.getRequiredType(), injection.getRequiredQualifiers()).stream()
                .anyMatch(builderSystems::isBuilderSystem);
    }

    /** {@code Class#method/Sub/Agent} -> {@code Class#method} */
    static String systemId(String nodeId) {
        int slash = nodeId.indexOf('/');
        return slash < 0 ? nodeId : nodeId.substring(0, slash);
    }
}
