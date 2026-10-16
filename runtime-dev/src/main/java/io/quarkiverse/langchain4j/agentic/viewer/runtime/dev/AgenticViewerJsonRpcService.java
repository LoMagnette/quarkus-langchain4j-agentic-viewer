package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.ExecutionEvent;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunSummary;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunTrace;
import io.smallrye.mutiny.Multi;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;

/**
 * JSON-RPC service of the Agentic Viewer Dev UI (method names: {@code quarkus-langchain4j-agentic-viewer_<method>}).
 */
public class AgenticViewerJsonRpcService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgenticSystemRegistry registry;
    private final RunManager runManager;

    @Inject
    public AgenticViewerJsonRpcService(AgenticSystemRegistry registry, RunManager runManager) {
        this.registry = registry;
        this.runManager = runManager;
    }

    /**
     * @return a summary of every agentic system, in registration order
     */
    public List<AgenticSystemSummary> listSystems() {
        return registry.systems().stream().map(AgenticSystemSummary::of).toList();
    }

    /**
     * @return the system with its full topology
     * @throws IllegalArgumentException for an unknown id (e.g. a system removed by a hot reload)
     */
    public AgenticSystem getTopology(String systemId) {
        return system(systemId);
    }

    /**
     * @return the JSON schema of the run form: an object with one property per input, the required ones listed
     * @throws IllegalArgumentException for an unknown id
     */
    public Map<String, Object> getInputSchema(String systemId) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (SystemInput input : system(systemId).inputs()) {
            properties.put(input.name(), parse(input.jsonSchema()));
            if (input.required()) {
                required.add(input.name());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    /**
     * Starts a run and returns at once (T2.2): the run executes on a worker thread and reports its events.
     *
     * @param inputs input values by name, as filled in the run form
     * @return the run id
     * @throws IllegalArgumentException for an unknown id
     */
    public String startRun(String systemId, Map<String, Object> inputs) {
        return runManager.startRun(systemId, inputs);
    }

    /**
     * Streams the events of a run: those already emitted, then the live ones; completes when the run ends (T2.5).
     */
    public Multi<ExecutionEvent> streamRun(String runId) {
        return runManager.stream(runId);
    }

    /**
     * @return the runs of a system kept in the history, newest first (T2.2)
     */
    public List<RunSummary> listRuns(String systemId) {
        return runManager.listRuns(systemId);
    }

    /**
     * @return the run with all its events
     * @throws IllegalArgumentException for an unknown (or evicted) run
     */
    public RunTrace getRun(String runId) {
        return runManager.trace(runId).orElseThrow(() -> new IllegalArgumentException("Unknown run: " + runId));
    }

    /**
     * Cancels a running run, best effort.
     *
     * @return whether the run was running and is now cancelled
     */
    public boolean cancelRun(String runId) {
        return runManager.cancel(runId);
    }

    private AgenticSystem system(String systemId) {
        return registry.find(systemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agentic system: " + systemId));
    }

    private static Map<String, Object> parse(String jsonSchema) {
        try {
            return MAPPER.readValue(jsonSchema, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid input schema: " + jsonSchema, e);
        }
    }
}
