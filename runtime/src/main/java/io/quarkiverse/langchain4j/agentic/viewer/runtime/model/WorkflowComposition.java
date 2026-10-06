package io.quarkiverse.langchain4j.agentic.viewer.runtime.model;

import java.util.List;

/**
 * Represents the composition of agents within a workflow.
 *
 * @param parentAgentId the parent workflow agent
 * @param childAgentIds the child agents in the workflow
 * @param type the type of composition (SEQUENCE, PARALLEL, CONDITIONAL, LOOP)
 */
public record WorkflowComposition(
        String parentAgentId,
        List<String> childAgentIds,
        CompositionType type) {
}
