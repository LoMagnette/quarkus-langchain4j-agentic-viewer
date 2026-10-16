package io.quarkiverse.langchain4j.agentic.viewer.deployment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgentParameter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgentType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.CompositionType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.DataFlow;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.ImperativePattern;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.ToolNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.ToolParameter;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.WorkflowComposition;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.model.AgenticGraphBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticDescriptorsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticRootsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scanner.DeclarativeAgentScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.AgenticSystemsBuildItem;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scanner.ImperativeAgentScanner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerJsonRpcService;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.AgenticViewerRecorder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgenticGraph;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * Main build processor for the Agentic Viewer extension.
 */
public class AgenticViewerProcessor {

    private static final Logger LOG = Logger.getLogger(AgenticViewerProcessor.class);
    private static final String FEATURE = "langchain4j-agentic-viewer";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Describes every agent method of the application (T1.2).
     */
    @BuildStep
    AgenticDescriptorsBuildItem scanAgenticAnnotations(CombinedIndexBuildItem combinedIndex) {
        return new AgenticDescriptorsBuildItem(new AgenticAnnotationScanner().scan(combinedIndex.getIndex()));
    }

    /**
     * Keeps the agents no other agent uses as roots of agentic systems, with their sub-agents nested (T1.3).
     */
    @BuildStep
    AgenticRootsBuildItem detectRootSystems(AgenticDescriptorsBuildItem descriptors) {
        return new AgenticRootsBuildItem(new RootSystemDetector().detect(descriptors.getDescriptors()));
    }

    /**
     * Builds the topology of every declarative system (T1.4).
     */
    @BuildStep
    AgenticSystemsBuildItem buildTopologies(AgenticRootsBuildItem roots) {
        return new AgenticSystemsBuildItem(new TopologyBuilder().build(roots.getRoots()));
    }

    /**
     * Hands the declarative systems to runtime-dev, where runs and their events will refer to the same node ids.
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(ExecutionTime.STATIC_INIT)
    void recordSystems(AgenticSystemsBuildItem systems, AgenticViewerRecorder recorder) {
        recorder.setSystems(systems.getSystems());
    }

    /**
     * Discovers agents and tools from the codebase using Jandex and ASM (prototype view, see TASK-26).
     */
    @BuildStep
    AgenticGraphBuildItem discoverAgents(
            CombinedIndexBuildItem combinedIndex,
            ApplicationArchivesBuildItem archives) {

        LOG.info("Agentic Viewer: Starting agent discovery...");

        // Scan declarative patterns using Jandex
        DeclarativeAgentScanner declarativeScanner = new DeclarativeAgentScanner(combinedIndex.getIndex());
        AgenticGraph graph = declarativeScanner.scan();

        LOG.infof("Agentic Viewer: Found %d agents, %d tools, %d data flows, %d compositions",
                graph.agents().size(), graph.tools().size(),
                graph.dataFlows().size(), graph.compositions().size());

        // Log agent details for debugging
        for (var agent : graph.agents()) {
            LOG.debugf("Agentic Viewer: Agent '%s' (type=%s, class=%s)",
                    agent.name(), agent.type(), agent.className());
        }

        // Detect imperative patterns using ASM.
        // The langchain4j libraries are indexed archives too, but their own builder calls are not part of the application.
        List<ApplicationArchive> applicationArchives = archives.getAllApplicationArchives().stream()
                .filter(archive -> archive == archives.getRootArchive() || !isLangChain4jLibrary(archive))
                .toList();
        ImperativeAgentScanner imperativeScanner = new ImperativeAgentScanner();
        graph = imperativeScanner.enhanceGraph(graph, applicationArchives);

        if (graph.hasImperativePatterns()) {
            LOG.infof("Agentic Viewer: Imperative patterns detected in: %s",
                    String.join(", ", graph.imperativeLocations()));

            // Bridge imperative patterns to declarative agents
            graph = bridgeImperativeToDeclarative(graph);
        }

        return new AgenticGraphBuildItem(graph);
    }

    private static boolean isLangChain4jLibrary(ApplicationArchive archive) {
        ArtifactKey key = archive.getKey();
        if (key == null) {
            return false;
        }
        return "dev.langchain4j".equals(key.getGroupId())
                || ("io.quarkiverse.langchain4j".equals(key.getGroupId())
                        && key.getArtifactId().startsWith("quarkus-langchain4j"));
    }

    /**
     * Records the agentic graph at runtime.
     * Dev mode only: the recorder lives in the runtime-dev artifact, which is absent from prod builds.
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(ExecutionTime.STATIC_INIT)
    void recordGraph(
            AgenticGraphBuildItem graphItem,
            AgenticViewerRecorder recorder) {
        recorder.setAgenticGraph(graphItem.getGraph());
    }

    /**
     * Registers the Dev UI card and pages (only in dev mode).
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem registerDevUI(AgenticGraphBuildItem graphItem, AgenticSystemsBuildItem systems) {

        AgenticGraph graph = graphItem.getGraph();

        CardPageBuildItem card = new CardPageBuildItem();

        // Build time data for the card and diagram
        card.addBuildTimeData("agentCount", graph.agents().size());
        card.addBuildTimeData("toolCount", graph.tools().size());
        card.addBuildTimeData("hasImperative", graph.hasImperativePatterns());
        card.addBuildTimeData("imperativeLocations", graph.imperativeLocations());

        // Serialize the full graph for the diagram
        card.addBuildTimeData("agenticGraph", serializeGraph(graph));

        // Topology of the declarative systems (T1.4), for the system list and graph pages
        card.addBuildTimeData("agenticSystems", systems.getSystems());

        // Add the main diagram page
        card.addPage(Page.webComponentPageBuilder()
                .title("Agentic Diagram")
                .icon("font-awesome-solid:diagram-project")
                .componentLink("qwc-agentic-viewer.js"));

        // Agentic systems page (T1.5): the card shows the number of systems as the label of its link
        card.addPage(Page.webComponentPageBuilder()
                .title("Agentic Systems")
                .icon("font-awesome-solid:robot")
                .staticLabel(String.valueOf(systems.getSystems().size()))
                .componentLink("qwc-agentic-systems.js"));

        // T0.8 spike: Cytoscape.js + ELK rendering, to be replaced by the real graph component (T1.6)
        card.addPage(Page.webComponentPageBuilder()
                .title("Graph (spike)")
                .icon("font-awesome-solid:diagram-project")
                .componentLink("qwc-agentic-graph-spike.js"));

        return card;
    }

    /**
     * Registers the JSON-RPC service used by the Dev UI pages (only in dev mode, like the service itself).
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem registerJsonRpc() {
        return new JsonRPCProvidersBuildItem(AgenticViewerJsonRpcService.class);
    }

    /**
     * Bridges imperative patterns to declarative agents.
     * For each imperative workflow pattern that references known declarative agent classes,
     * creates a synthetic parent node and WorkflowComposition linking them.
     */
    private AgenticGraph bridgeImperativeToDeclarative(AgenticGraph graph) {
        // Build lookup: class name -> ALL agent IDs from that class
        // A single class can have multiple @Agent methods, each producing its own AgentNode
        Map<String, List<String>> classToAllAgentIds = new HashMap<>();
        for (AgentNode agent : graph.agents()) {
            if (agent.className() != null) {
                classToAllAgentIds.computeIfAbsent(agent.className(), k -> new ArrayList<>()).add(agent.id());
                // Also register by simple name
                String simpleName = agent.className().contains(".")
                        ? agent.className().substring(agent.className().lastIndexOf('.') + 1)
                        : agent.className();
                classToAllAgentIds.computeIfAbsent(simpleName, k -> new ArrayList<>()).add(agent.id());
            }
        }

        List<AgentNode> syntheticAgents = new ArrayList<>();
        List<WorkflowComposition> syntheticCompositions = new ArrayList<>();
        AtomicInteger syntheticIdCounter = new AtomicInteger(0);

        for (ImperativePattern pattern : graph.imperativePatterns()) {
            List<String> classRefs = pattern.agentClassRefs();
            if (classRefs == null || classRefs.isEmpty()) {
                continue;
            }

            // Resolve class refs to agent IDs (expand each class to ALL its agents)
            List<String> childAgentIds = new ArrayList<>();
            for (String classRef : classRefs) {
                List<String> agentIds = classToAllAgentIds.get(classRef);
                if (agentIds == null) {
                    // Try simple name
                    String simpleName = classRef.contains(".")
                            ? classRef.substring(classRef.lastIndexOf('.') + 1)
                            : classRef;
                    agentIds = classToAllAgentIds.get(simpleName);
                }
                if (agentIds != null) {
                    childAgentIds.addAll(agentIds);
                }
            }

            if (childAgentIds.isEmpty()) {
                continue;
            }

            // Determine the workflow type from builder types
            CompositionType compositionType = CompositionType.SEQUENCE;
            AgentType agentType = AgentType.SEQUENCE;
            for (String builderType : pattern.builderTypes()) {
                switch (builderType) {
                    case "PARALLEL" -> { compositionType = CompositionType.PARALLEL; agentType = AgentType.PARALLEL; }
                    case "CONDITIONAL" -> { compositionType = CompositionType.CONDITIONAL; agentType = AgentType.CONDITIONAL; }
                    case "LOOP" -> { compositionType = CompositionType.LOOP; agentType = AgentType.LOOP; }
                    case "SEQUENCE" -> { compositionType = CompositionType.SEQUENCE; agentType = AgentType.SEQUENCE; }
                }
            }

            // Check if the pattern's own class already has declarative agents
            List<String> ownAgentIds = classToAllAgentIds.getOrDefault(pattern.className(), List.of());

            if (!ownAgentIds.isEmpty()) {
                // The pattern class is itself a declarative agent class.
                // Remove self-references from children (parent cannot be its own child).
                List<String> filteredChildren = childAgentIds.stream()
                        .filter(id -> !ownAgentIds.contains(id))
                        .distinct()
                        .toList();

                if (filteredChildren.isEmpty()) {
                    // All children are from the same class as the pattern - this is a
                    // self-referencing scenario (e.g., sequenceBuilder().add(SameClass.class)).
                    // Create a synthetic parent that composes the class's own agents.
                    String syntheticId = "imperative_" + syntheticIdCounter.incrementAndGet();
                    String className = pattern.className();
                    String simpleName = className.contains(".")
                            ? className.substring(className.lastIndexOf('.') + 1) : className;

                    AgentNode syntheticParent = new AgentNode(
                            syntheticId,
                            simpleName + " Workflow",
                            agentType,
                            "Imperative " + compositionType.name().toLowerCase() + " workflow",
                            null, null, null, List.of(),
                            className, pattern.methodName(),
                            false, null, List.of());

                    syntheticAgents.add(syntheticParent);
                    // Deduplicate the original childAgentIds (which are all from this class)
                    List<String> deduped = childAgentIds.stream().distinct().toList();
                    syntheticCompositions.add(new WorkflowComposition(syntheticId, deduped, compositionType));

                    LOG.infof("Agentic Viewer: Bridged self-referencing imperative %s in %s.%s to %d agents",
                            compositionType, simpleName, pattern.methodName(), deduped.size());
                } else {
                    // Mix of own class agents and external agents.
                    // Use the first own agent as parent if no composition exists yet.
                    String parentId = ownAgentIds.get(0);
                    boolean hasComposition = graph.compositions().stream()
                            .anyMatch(c -> c.parentAgentId().equals(parentId));
                    if (!hasComposition) {
                        syntheticCompositions.add(new WorkflowComposition(parentId, filteredChildren, compositionType));
                        LOG.infof("Agentic Viewer: Bridged existing agent %s to %d imperative children",
                                parentId, filteredChildren.size());
                    }
                }
            } else {
                // Pattern class is not a declarative agent - create a synthetic parent
                String syntheticId = "imperative_" + syntheticIdCounter.incrementAndGet();
                String className = pattern.className();
                String simpleName = className.contains(".")
                        ? className.substring(className.lastIndexOf('.') + 1) : className;

                AgentNode syntheticParent = new AgentNode(
                        syntheticId,
                        simpleName,
                        agentType,
                        "Imperative " + compositionType.name().toLowerCase() + " workflow",
                        null, null, null, List.of(),
                        className, pattern.methodName(),
                        false, null, List.of());

                syntheticAgents.add(syntheticParent);
                List<String> deduped = childAgentIds.stream().distinct().toList();
                syntheticCompositions.add(new WorkflowComposition(syntheticId, deduped, compositionType));

                LOG.infof("Agentic Viewer: Bridged imperative %s in %s.%s to %d declarative agents",
                        compositionType, simpleName, pattern.methodName(), deduped.size());
            }
        }

        if (!syntheticAgents.isEmpty() || !syntheticCompositions.isEmpty()) {
            return graph.withAdditionalAgentsAndCompositions(syntheticAgents, syntheticCompositions);
        }

        return graph;
    }

    /**
     * Serializes the graph to a map structure that can be passed as build-time data.
     * Records need to be converted to Maps for proper JSON serialization.
     */
    private Map<String, Object> serializeGraph(AgenticGraph graph) {
        Map<String, Object> result = new HashMap<>();
        result.put("agents", serializeAgents(graph.agents()));
        result.put("tools", serializeTools(graph.tools()));
        result.put("dataFlows", serializeDataFlows(graph.dataFlows()));
        result.put("compositions", serializeCompositions(graph.compositions()));
        result.put("hasImperativePatterns", graph.hasImperativePatterns());
        result.put("imperativeLocations", graph.imperativeLocations());
        result.put("imperativePatterns", serializeImperativePatterns(graph.imperativePatterns()));
        return result;
    }

    private List<Map<String, Object>> serializeAgents(List<AgentNode> agents) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentNode agent : agents) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", agent.id());
            map.put("name", agent.name());
            map.put("type", agent.type().name());
            map.put("description", agent.description());
            map.put("systemMessage", agent.systemMessage());
            map.put("userMessage", agent.userMessage());
            map.put("outputKey", agent.outputKey());
            map.put("toolIds", agent.toolIds());
            map.put("className", agent.className());
            map.put("methodName", agent.methodName());
            map.put("isRemote", agent.isRemote());
            map.put("remoteUrl", agent.remoteUrl());
            map.put("inputParameters", serializeAgentParameters(agent.inputParameters()));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> serializeAgentParameters(List<AgentParameter> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (params != null) {
            for (AgentParameter param : params) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", param.name());
                map.put("type", param.type());
                map.put("isMemoryId", param.isMemoryId());
                result.add(map);
            }
        }
        return result;
    }

    private List<Map<String, Object>> serializeTools(List<ToolNode> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolNode tool : tools) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", tool.id());
            map.put("name", tool.name());
            map.put("description", tool.description());
            map.put("className", tool.className());
            map.put("methodName", tool.methodName());
            map.put("returnType", tool.returnType());
            map.put("parameters", serializeParameters(tool.parameters()));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> serializeParameters(List<ToolParameter> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (params != null) {
            for (ToolParameter param : params) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", param.name());
                map.put("type", param.type());
                result.add(map);
            }
        }
        return result;
    }

    private List<Map<String, Object>> serializeDataFlows(List<DataFlow> flows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DataFlow flow : flows) {
            Map<String, Object> map = new HashMap<>();
            map.put("sourceAgentId", flow.sourceAgentId());
            map.put("targetAgentId", flow.targetAgentId());
            map.put("variableName", flow.variableName());
            map.put("outputKey", flow.outputKey());
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> serializeCompositions(List<WorkflowComposition> compositions) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (WorkflowComposition comp : compositions) {
            Map<String, Object> map = new HashMap<>();
            map.put("parentAgentId", comp.parentAgentId());
            map.put("childAgentIds", comp.childAgentIds());
            map.put("type", comp.type().name());
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> serializeImperativePatterns(List<ImperativePattern> patterns) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (patterns != null) {
            for (ImperativePattern pattern : patterns) {
                Map<String, Object> map = new HashMap<>();
                map.put("className", pattern.className());
                map.put("methodName", pattern.methodName());
                map.put("builderTypes", pattern.builderTypes());
                map.put("agentClassRefs", pattern.agentClassRefs());
                result.add(map);
            }
        }
        return result;
    }
}
