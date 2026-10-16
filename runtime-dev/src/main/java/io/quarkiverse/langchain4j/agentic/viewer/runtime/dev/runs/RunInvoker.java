package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs;

import java.util.Map;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;

/**
 * Calls an agentic system with the inputs of a run, on the run thread.
 */
@FunctionalInterface
public interface RunInvoker {

    /**
     * @param inputs input values by name, as sent by the run form
     * @return the result of the root agent
     * @throws Exception any failure of the system, or an {@link IllegalArgumentException} for inputs that cannot be used
     */
    Object invoke(AgenticSystem system, Map<String, Object> inputs) throws Exception;
}
