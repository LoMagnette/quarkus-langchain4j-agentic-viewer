package io.quarkiverse.langchain4j.agentic.viewer.deployment.builder;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The one BuilderSystemBeans of a build: the steps telling builder systems apart share what it read of the bytecode.
 */
public final class BuilderSystemBeansBuildItem extends SimpleBuildItem {

    private final BuilderSystemBeans beans;

    public BuilderSystemBeansBuildItem(BuilderSystemBeans beans) {
        this.beans = beans;
    }

    public BuilderSystemBeans getBeans() {
        return beans;
    }
}
