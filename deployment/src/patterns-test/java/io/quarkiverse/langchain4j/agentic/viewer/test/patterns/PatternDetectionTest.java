package io.quarkiverse.langchain4j.agentic.viewer.test.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.PlannerTypeDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;

/**
 * With langchain4j-agentic-patterns on the class path (profile "patterns"), build-time scanning tags each planner
 * node with its pattern and class.
 */
class PatternDetectionTest {

    @Test
    void plannersAreTaggedWithTheirPattern() throws Exception {
        Index index = Index.of(PatternSystems.Goal.class, PatternSystems.Peers.class, PatternSystems.Board.class,
                PatternSystems.Beliefs.class, PatternSystems.Debate.class, PatternSystems.Vote.class,
                PatternSystems.Writer.class);
        List<AgenticSystem> systems = new TopologyBuilder(PlannerTypeDetector.of(index, getClass().getClassLoader()))
                .build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(index)));
        Map<String, String> planners = systems.stream().collect(Collectors.toMap(AgenticSystem::name,
                s -> s.root().plannerInfo().patternType() + " " + s.root().plannerInfo().plannerClass()));
        assertEquals(Map.of(
                "Goal", "GOAP dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner",
                "Peers", "P2P dev.langchain4j.agentic.patterns.p2p.P2PPlanner",
                "Board", "BLACKBOARD dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner",
                "Beliefs", "BDI dev.langchain4j.agentic.patterns.bdi.BDIPlanner",
                "Debate", "DEBATE dev.langchain4j.agentic.patterns.debate.DebatePlanner",
                "Vote", "VOTING dev.langchain4j.agentic.patterns.voting.VotingPlanner"), planners);
    }
}
