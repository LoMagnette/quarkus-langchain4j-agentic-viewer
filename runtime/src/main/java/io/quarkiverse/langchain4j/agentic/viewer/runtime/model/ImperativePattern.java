package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

import java.util.List;

/**
 * Represents an imperative pattern detected in the codebase.
 *
 * @param className the class where the pattern was found
 * @param methodName the method where the pattern was found (if available)
 * @param builderTypes list of builder types used (SEQUENCE, PARALLEL, CONDITIONAL, LOOP, AGENT)
 * @param agentClassRefs any class references detected in agentBuilder() calls
 */
public record ImperativePattern(
        String className,
        String methodName,
        List<String> builderTypes,
        List<String> agentClassRefs) {
}
