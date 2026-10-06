package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * Where an agentic system is defined: the root interface and method for declarative systems,
 * or the call site of {@code build()} for builder systems.
 *
 * @param className declaring class (declarative) or class containing the call site (builder)
 * @param methodName root method (declarative) or method containing the call site (builder)
 * @param callSite call site description for builder systems (e.g. {@code MyClass.java:42}), null for declarative ones
 */
public record SourceRef(String className, String methodName, String callSite) {
}
