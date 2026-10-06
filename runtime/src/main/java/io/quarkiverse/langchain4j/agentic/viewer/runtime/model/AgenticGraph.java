package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete graph of agents, tools, and their relationships.
 *
 * @param agents list of detected agents
 * @param tools list of detected tools
 * @param dataFlows list of data flow relationships between agents
 * @param compositions list of workflow compositions
 * @param hasImperativePatterns whether imperative patterns were detected
 * @param imperativeLocations class names where imperative patterns were found
 * @param imperativePatterns detailed information about imperative patterns
 */
public record AgenticGraph(
        List<AgentNode> agents,
        List<ToolNode> tools,
        List<DataFlow> dataFlows,
        List<WorkflowComposition> compositions,
        boolean hasImperativePatterns,
        List<String> imperativeLocations,
        List<ImperativePattern> imperativePatterns) {

    /**
     * Creates an empty graph.
     */
    public static AgenticGraph empty() {
        return new AgenticGraph(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                List.of());
    }

    /**
     * Creates a new graph with the imperative pattern information added.
     */
    public AgenticGraph withImperativePatterns(boolean hasImperative, List<String> locations) {
        return new AgenticGraph(
                this.agents,
                this.tools,
                this.dataFlows,
                this.compositions,
                hasImperative,
                locations,
                List.of());
    }

    /**
     * Creates a new graph with the detailed imperative pattern information added.
     */
    public AgenticGraph withImperativePatterns(boolean hasImperative, List<String> locations,
            List<ImperativePattern> patterns) {
        return new AgenticGraph(
                this.agents,
                this.tools,
                this.dataFlows,
                this.compositions,
                hasImperative,
                locations,
                patterns);
    }

    /**
     * Creates a new graph with additional agents and compositions merged in
     * (used for bridging imperative patterns to declarative agents).
     */
    public AgenticGraph withAdditionalAgentsAndCompositions(
            List<AgentNode> additionalAgents,
            List<WorkflowComposition> additionalCompositions) {
        List<AgentNode> mergedAgents = new ArrayList<>(this.agents);
        mergedAgents.addAll(additionalAgents);
        List<WorkflowComposition> mergedCompositions = new ArrayList<>(this.compositions);
        mergedCompositions.addAll(additionalCompositions);
        return new AgenticGraph(
                mergedAgents,
                this.tools,
                this.dataFlows,
                mergedCompositions,
                this.hasImperativePatterns,
                this.imperativeLocations,
                this.imperativePatterns);
    }
}
