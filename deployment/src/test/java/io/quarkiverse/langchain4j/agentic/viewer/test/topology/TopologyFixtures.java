package io.quarkiverse.langchain4j.agentic.viewer.test.topology;

import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AiAgentDetails;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.Guard;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.NodeKind;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PlannerInfo;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemOrigin;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.ToolInfo;

/**
 * Topologies exercising every record and field of the model.
 */
public final class TopologyFixtures {

    private TopologyFixtures() {
    }

    /**
     * The StoryCreator sample: a loop nested in a sequence.
     */
    public static AgenticSystem storyCreator() {
        AgentNode draft = new AgentNode("StoryCreator.create/draft", "draft", NodeKind.AI_AGENT,
                "Writes the first draft of the story", List.of("topic"), "story", false, false, List.of(), null, null, null);
        AgentNode score = new AgentNode("StoryCreator.create/polish/score", "score", NodeKind.AI_AGENT,
                "Scores the style of the story", List.of("story"), "score", false, false, List.of(), null, null, null);
        AgentNode edit = new AgentNode("StoryCreator.create/polish/edit", "edit", NodeKind.AI_AGENT,
                "Rewrites the story with a better style", List.of("story"), "story", false, false, List.of(), null, null, null);
        AgentNode polish = new AgentNode("StoryCreator.create/polish", "polish", NodeKind.LOOP,
                "Polishes the story until its style score is at least 0.8", List.of("story"), "story", false, false,
                List.of(score, edit), Guard.loopExit("goodEnough", 5, true), null, null);
        AgentNode create = new AgentNode("StoryCreator.create", "create", NodeKind.SEQUENCE,
                "Drafts a story, then polishes its style", List.of("topic"), "story", false, false,
                List.of(draft, polish), null, null, null);
        return new AgenticSystem("StoryCreator.create", "StoryCreator", create.id(), SystemOrigin.DECLARATIVE,
                new SourceRef("io.quarkiverse.langchain4j.agentic.viewer.it.samples.StoryCreator", "create", null),
                List.of(new SystemInput("topic", "java.lang.String", "{\"type\":\"string\"}", true)),
                create);
    }

    /**
     * A conditional with guarded branches and a GOAP planner, with optional/async flags and AI agent details.
     */
    public static AgenticSystem routerWithPlanner() {
        AgentNode medical = new AgentNode("Router.ask/experts/medical", "medical", NodeKind.AI_AGENT, null,
                List.of("request"), "response", false, true, List.of(), Guard.activation("isMedical"), null,
                new AiAgentDetails("You are a doctor.", "Answer: {{request}}", "fast",
                        List.of(new ToolInfo("lookup", "com.example.MedicalTools", "lookup", "Looks up a drug")),
                        List.of("mcp:pharmacy")));
        AgentNode legal = new AgentNode("Router.ask/experts/legal", "legal", NodeKind.AI_AGENT, null,
                List.of("request"), "response", true, false, List.of(), Guard.activation("lambda"), null, null);
        AgentNode experts = new AgentNode("Router.ask/experts", "experts", NodeKind.CONDITIONAL, null,
                List.of("request"), "response", false, false, List.of(medical, legal), null, null, null);
        AgentNode planner = new AgentNode("Router.ask/plan", "plan", NodeKind.PLANNER, "Goal-oriented planning",
                List.of("city"), "itinerary", false, false, List.of(),
                null, new PlannerInfo("dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner", PatternType.GOAP,
                        Map.of("goal", "itinerary")), null);
        AgentNode root = new AgentNode("Router.ask", "ask", NodeKind.SEQUENCE, null, List.of("request", "city"),
                "response", false, false, List.of(experts, planner), null, null, null);
        return new AgenticSystem("Router.ask", "Router", root.id(), SystemOrigin.MIXED,
                new SourceRef("com.example.Router", "ask", "Router.java:42"),
                List.of(new SystemInput("request", "java.lang.String", "{\"type\":\"string\"}", true),
                        new SystemInput("city", "java.lang.String", "{\"type\":\"string\"}", false)),
                root);
    }
}
