package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.patterns.bdi.BDIPlanner;
import dev.langchain4j.agentic.patterns.bdi.Desire;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderTopology;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgentNode;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Builder systems with a BDI, debate or voting planner are tagged with their pattern, as declarative ones. Their BDI
 * desires stay unknown: the planner keeps them private.
 */
@QuarkusTest
class BuilderPatternTypesTest {

    @Inject
    ChatModel chatModel;

    private Object agent(Class<?> agentInterface) {
        return AgenticServices.agentBuilder(agentInterface).chatModel(chatModel).build();
    }

    private AgentNode planner(Supplier<Planner> planner, Object... subAgents) {
        UntypedAgent root = AgenticServices.plannerBuilder().name("plan").outputKey("result").subAgents(subAgents)
                .planner(planner).build();
        return BuilderTopology.system("test#system", "TestSystem", new SourceRef("Test", "system", null),
                (AgentInstance) root).root();
    }

    @Test
    void bdi() {
        AgentNode node = planner(() -> new BDIPlanner(List.of(Desire.of("fix", 1, "bugReport", "patch",
                ReleaseCaptain.BugFixer.class))), agent(ReleaseCaptain.BugFixer.class));
        assertEquals(PatternType.BDI, node.plannerInfo().patternType());
        assertEquals(BDIPlanner.class.getName(), node.plannerInfo().plannerClass());
        assertNull(node.plannerInfo().desires(), "private to the planner");
    }

    @Test
    void debate() {
        AgentNode node = planner(DebatePlanner::new, agent(DesignDebate.Advocate.class), agent(DesignDebate.Critic.class),
                agent(DesignDebate.Judge.class));
        assertEquals(PatternType.DEBATE, node.plannerInfo().patternType());
    }

    @Test
    void voting() {
        AgentNode node = planner(VotingPlanner::new, agent(ReviewPanel.StyleReviewer.class),
                agent(ReviewPanel.SecurityReviewer.class));
        assertEquals(PatternType.VOTING, node.plannerInfo().patternType());
    }
}
