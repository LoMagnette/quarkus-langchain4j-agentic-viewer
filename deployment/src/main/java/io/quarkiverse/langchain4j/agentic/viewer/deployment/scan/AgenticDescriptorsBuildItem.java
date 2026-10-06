package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.List;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Every agent method of the application found by {@link AgenticAnnotationScanner}.
 */
public final class AgenticDescriptorsBuildItem extends SimpleBuildItem {

    private final List<AgentDescriptor> descriptors;

    public AgenticDescriptorsBuildItem(List<AgentDescriptor> descriptors) {
        this.descriptors = List.copyOf(descriptors);
    }

    public List<AgentDescriptor> getDescriptors() {
        return descriptors;
    }
}
