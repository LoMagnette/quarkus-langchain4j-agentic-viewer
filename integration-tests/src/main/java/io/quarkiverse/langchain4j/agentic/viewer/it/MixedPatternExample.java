package io.quarkiverse.langchain4j.agentic.viewer.it;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryWriter;

/**
 * Example that mixes declarative and imperative patterns.
 *
 * The createWorkflow() method uses AgenticServices.sequenceBuilder()
 * imperatively, referencing declarative agent interfaces as sub-agents.
 *
 * The viewer should detect both and bridge them in the graph:
 * - Declarative agents (StoryWriter.IntroWriter, StoryWriter.AdventureWriter) as AgentNodes
 * - Imperative sequenceBuilder() as a synthetic parent
 * - Composition linking the imperative parent to declarative children
 */
public class MixedPatternExample {

    /**
     * Creates a sequence workflow imperatively using the builder API.
     * The viewer should detect this and bridge it to the declarative agents.
     */
    public UntypedAgent createWorkflow() {
        return AgenticServices.sequenceBuilder()
                .subAgents(StoryWriter.IntroWriter.class, StoryWriter.AdventureWriter.class)
                .outputKey("adventure")
                .build();
    }
}
