package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

/**
 * A bean found at build time that may be an agentic system built with the {@code AgenticServices} builders.
 *
 * @param beanId Arc bean identifier
 * @param declaringClass class declaring the producer
 * @param member producer method or field
 * @param beanName {@code @Named} name, null for none
 * @param typeName the produced type
 */
public record BuilderCandidate(String beanId, String declaringClass, String member, String beanName, String typeName) {
}
