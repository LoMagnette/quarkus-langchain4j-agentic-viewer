package io.quarkiverse.langchain4j.agentic.viewer.it.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
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
 * The planner nodes of the langchain4j-agentic-patterns samples are tagged with their pattern (T4.2).
 */
class PatternPlannerTypesTest {

    @Test
    void patternSamplesAreTagged() throws Exception {
        File samples = new File(TripGoalPlanner.class.getResource("TripGoalPlanner.class").toURI()).getParentFile();
        Index index = Index.of(samples);
        List<AgenticSystem> systems = new TopologyBuilder(PlannerTypeDetector.of(index, getClass().getClassLoader()))
                .build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(index)));
        Map<String, String> planners = systems.stream().collect(Collectors.toMap(AgenticSystem::name,
                s -> s.root().plannerInfo().patternType() + " " + s.root().plannerInfo().plannerClass()));
        assertEquals(Map.of(
                "TripGoalPlanner", "GOAP dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner",
                "ResearchTeam", "P2P dev.langchain4j.agentic.patterns.p2p.P2PPlanner",
                "IncidentResponder", "BLACKBOARD dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner"), planners);
    }
}
