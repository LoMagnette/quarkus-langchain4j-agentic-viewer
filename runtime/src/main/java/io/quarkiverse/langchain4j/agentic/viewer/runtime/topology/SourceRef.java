package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * Where an agentic system is defined: the root interface and method for declarative systems,
 * or the call site of {@code build()} for builder systems.
 *
 * @param className declaring class (declarative) or class containing the call site (builder)
 * @param methodName root method (declarative) or method containing the call site (builder)
 * @param callSite where a builder system reported by the transform was built ({@code com.example.App#method:42}),
 *        null for declarative systems and builder systems produced as CDI beans
 */
public record SourceRef(String className, String methodName, String callSite) {
}
