package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

import java.util.List;
import java.util.Map;

/**
 * A planner (supervisor, custom or pattern planner) chose what to run next.
 *
 * @param nodeId topology node of the planner
 * @param invocationId invocation of the planner workflow
 * @param selectedNodeIds nodes of the agents chosen to run next, empty when done
 * @param done whether the planner ended the workflow
 * @param extras pattern-specific information (GOAP path, P2P activations, blackboard candidates)
 */
public record PlannerDecision(String runId, long timestamp, String nodeId, String invocationId,
        List<String> selectedNodeIds, boolean done, Map<String, Object> extras) implements ExecutionEvent {

    public PlannerDecision {
        selectedNodeIds = selectedNodeIds == null ? List.of() : List.copyOf(selectedNodeIds);
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }
}
