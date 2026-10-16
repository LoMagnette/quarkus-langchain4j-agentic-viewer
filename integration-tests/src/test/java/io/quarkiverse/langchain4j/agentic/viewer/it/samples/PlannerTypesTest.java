package io.quarkiverse.langchain4j.agentic.viewer.it.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.RootSystemDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.PlannerTypeDetector;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.TopologyBuilder;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.AgenticSystem;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;

/**
 * The custom planner sample is tagged CUSTOM with its class (T4.2); without the patterns module nothing else could be.
 */
class PlannerTypesTest {

    @Test
    void customPlannerSample() throws Exception {
        File samples = new File(StoryWriter.class.getResource("StoryWriter.class").toURI()).getParentFile();
        Index index = Index.of(samples);
        List<AgenticSystem> systems = new TopologyBuilder(PlannerTypeDetector.of(index, getClass().getClassLoader()))
                .build(new RootSystemDetector().detect(new AgenticAnnotationScanner().scan(index)));
        AgenticSystem draftReviewer = systems.stream().filter(s -> s.name().equals("DraftReviewer")).findFirst().orElseThrow();
        assertEquals(PatternType.CUSTOM, draftReviewer.root().plannerInfo().patternType());
        assertEquals(DraftReviewer.DraftReviewPlanner.class.getName(), draftReviewer.root().plannerInfo().plannerClass());
    }
}
