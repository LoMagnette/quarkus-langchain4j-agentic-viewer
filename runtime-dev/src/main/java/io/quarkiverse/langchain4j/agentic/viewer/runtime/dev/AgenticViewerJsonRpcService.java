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
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.patterns.PatternAdapters;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.ApplicationRuns;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunManager;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunObservation;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunSummary;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.RunTrace;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.SeriesSummary;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.SeriesTrace;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.spi.TopologyContribution;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.CodeRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;
import io.smallrye.mutiny.Multi;

/**
 * JSON-RPC service of the Agentic Viewer Dev UI (method names: {@code quarkus-langchain4j-agentic-viewer_<method>}).
 */
public class AgenticViewerJsonRpcService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgenticSystemRegistry registry;
    private final RunManager runManager;
    private final PatternAdapters patternAdapters;
    private final SourceLocator sources;
    private final RunObservation observation;

    public AgenticViewerJsonRpcService(AgenticSystemRegistry registry, RunManager runManager) {
        this(registry, runManager, PatternAdapters.none());
    }

    public AgenticViewerJsonRpcService(AgenticSystemRegistry registry, RunManager runManager,
            PatternAdapters patternAdapters) {
        this(registry, runManager, patternAdapters, SourceLocator.ofDevMode(), null);
    }

    @Inject
    public AgenticViewerJsonRpcService(AgenticSystemRegistry registry, RunManager runManager,
            PatternAdapters patternAdapters, RunObservation observation) {
        this(registry, runManager, patternAdapters, SourceLocator.ofDevMode(), observation);
    }

    public AgenticViewerJsonRpcService(AgenticSystemRegistry registry, RunManager runManager,
            PatternAdapters patternAdapters, SourceLocator sources) {
        this(registry, runManager, patternAdapters, sources, null);
    }

    /**
     * @param observation attaches the Dev UI listener before a run if the startup did not yet (null in tests)
     */
    public AgenticViewerJsonRpcService(AgenticSystemRegistry registry, RunManager runManager,
            PatternAdapters patternAdapters, SourceLocator sources, RunObservation observation) {
        this.registry = registry;
        this.runManager = runManager;
        this.patternAdapters = patternAdapters;
        this.sources = sources;
        this.observation = observation;
    }

    /**
     * The application code behind a node, for the Dev UI to open in the IDE: its code references, then its tools.
     *
     * @return per piece of code: {@code role}, {@code className}, {@code member}, {@code resource}, and when found in
     *         the application sources {@code fileName} ({@code file://} URI), {@code lang} and {@code lineNumber}
     * @throws IllegalArgumentException for an unknown system or node
     */
    public List<Map<String, Object>> getNodeSources(String systemId, String nodeId) {
        AgentNode node = find(system(systemId).root(), nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node " + nodeId + " in " + systemId);
        }
        List<CodeRef> code = new ArrayList<>(node.code());
        if (node.aiDetails() != null) {
            node.aiDetails().tools().forEach(tool -> code.add(CodeRef.method(null, tool.declaringClass(), tool.methodName())));
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (CodeRef ref : code) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", ref.role() == null ? "TOOL" : ref.role().name());
            entry.put("className", ref.className());
            entry.put("member", ref.member());
            entry.put("resource", ref.resource());
            sources.locate(ref).ifPresent(location -> {
                entry.put("fileName", location.fileName());
                entry.put("lang", location.lang());
                entry.put("lineNumber", location.lineNumber());
            });
            entries.add(entry);
        }
        return entries;
    }

    private static AgentNode find(AgentNode node, String id) {
        if (node.id().equals(id)) {
            return node;
        }
        for (AgentNode child : node.children()) {
            AgentNode found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * The path each goal-oriented (GOAP) planner of a system would take for the given keys, before running it: the
     * same search as the planner's, on the topology.
     *
     * @param inputKeys keys known when the planner starts (the inputs filled in the run form)
     * @return the child node ids in call order, by GOAP planner node id; empty without langchain4j-agentic-patterns or
     *         GOAP planner, an empty list when no path reaches the goal
     */
    public Map<String, List<String>> previewGoalPath(String systemId, List<String> inputKeys) {
        AgenticSystem system = system(systemId);
        Map<String, List<String>> paths = new LinkedHashMap<>();
        patternAdapters.adapterFor(PatternType.GOAP).ifPresent(adapter -> goalPlanners(system.root()).forEach(node -> paths
                .put(node.id(), adapter.previewPath(node, inputKeys == null ? List.of() : inputKeys).orElse(List.of()))));
        return paths;
    }

    private static List<AgentNode> goalPlanners(AgentNode node) {
        List<AgentNode> planners = new ArrayList<>();
        if (node.plannerInfo() != null && node.plannerInfo().patternType() == PatternType.GOAP) {
            planners.add(node);
        }
        node.children().forEach(child -> planners.addAll(goalPlanners(child)));
        return planners;
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
     * @return what the application's topology contributors draw in the planner nodes of a system, by node id (empty
     *         when none applies)
     * @throws IllegalArgumentException for an unknown id
     */
    public Map<String, TopologyContribution> getContributions(String systemId) {
        return registry.contributions(system(systemId).id());
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
        if (observation != null) {
            observation.ensureObserved(); // e.g. a run asked for right after a hot reload
        }
        return runManager.startRun(systemId, inputs);
    }

    /**
     * Streams the events of a run: those already emitted, then the live ones; completes when the run ends (T2.5).
     */
    public Multi<ExecutionEvent> streamRun(String runId) {
        return runManager.stream(runId);
    }

    /**
     * @return the runs of a system kept in the history, newest first (T2.2); empty for an unknown system (no run kept)
     */
    public List<RunSummary> listRuns(String systemId) {
        return runManager.listRuns(systemId);
    }

    /**
     * Starts a series (Agentic Test Runs page): the system runs {@code count} times with the same inputs, one run after
     * the other; returns at once.
     *
     * @param count 1 to {@value RunManager#MAX_SERIES_RUNS}
     * @return the series id
     * @throws IllegalArgumentException for an unknown id or a count out of bounds
     */
    public String startSeries(String systemId, Map<String, Object> inputs, int count) {
        if (observation != null) {
            observation.ensureObserved();
        }
        return runManager.startSeries(systemId, inputs, count);
    }

    /**
     * @return the series with the results of its runs so far (status, duration, tokens, output, error, agents invoked)
     * @throws IllegalArgumentException for an unknown (or dropped) series
     */
    public SeriesTrace getSeries(String seriesId) {
        return runManager.series(seriesId).orElseThrow(() -> new IllegalArgumentException("Unknown series: " + seriesId));
    }

    /**
     * @return the series of a system kept, newest first; empty for an unknown system (no series kept)
     */
    public List<SeriesSummary> listSeries(String systemId) {
        return runManager.listSeries(systemId);
    }

    /**
     * Stops a series: no further run starts, the one running is cancelled.
     *
     * @return whether it was running; false for an unknown or ended series (nothing to stop)
     */
    public boolean cancelSeries(String seriesId) {
        return runManager.cancelSeries(seriesId);
    }

    /**
     * @return whether the runs the application starts itself are recorded (config, Dev UI switch), and how many of the
     *         system were skipped; for any system id (nothing skipped for an unknown one)
     */
    public ApplicationRuns getApplicationRuns(String systemId) {
        return runManager.applicationRuns(systemId);
    }

    /**
     * Pauses or resumes the recording of application runs, for every system (nothing when the config turned them off).
     *
     * @return the new state, with the skipped runs of the system
     */
    public ApplicationRuns setRecordApplicationRuns(String systemId, boolean record) {
        runManager.recordApplicationRuns(record);
        return runManager.applicationRuns(systemId);
    }

    /**
     * Streams the application runs of a system as they start (their summary), until cancelled: the Dev UI refreshes its
     * history and may follow them (streamRun).
     */
    public Multi<RunSummary> streamApplicationRuns(String systemId) {
        return runManager.streamApplicationRuns(systemId);
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
     * @return whether the run was running and is now cancelled; false for an unknown or evicted run, or an application
     *         run (nothing to cancel: observed only)
     */
    public boolean cancelRun(String runId) {
        return runManager.cancel(runId);
    }

    /**
     * Answers the human-in-the-loop step a run waits on (AwaitingHumanInput).
     *
     * @param value the answer as typed: as is for a String step, JSON otherwise
     * @return true if a step was waiting and got the answer, false if none was waiting
     */
    public boolean provideHumanInput(String runId, String value) {
        if (runManager.run(runId).isEmpty()) {
            throw new IllegalArgumentException("Unknown run: " + runId);
        }
        return runManager.provideHumanInput(runId, value);
    }

    private AgenticSystem system(String systemId) {
        return registry.find(systemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agentic system: " + systemId));
    }

    private static Map<String, Object> parse(String jsonSchema) {
        if (jsonSchema == null) {
            return Map.of(); // any value
        }
        try {
            return MAPPER.readValue(jsonSchema, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid input schema: " + jsonSchema, e);
        }
    }
}
