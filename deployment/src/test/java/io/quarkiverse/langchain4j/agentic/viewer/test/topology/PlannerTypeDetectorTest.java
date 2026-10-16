package io.quarkiverse.langchain4j.agentic.viewer.test.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.workflow.impl.SequentialPlanner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.PlannerTypeDetector;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PlannerInfo;

/**
 * The planner class and pattern of a {@code @PlannerSupplier} come from its bytecode (T4.2).
 */
class PlannerTypeDetectorTest {

    static class CustomPlanner implements Planner {
        @Override
        public Action nextAction(PlanningContext context) {
            return done();
        }
    }

    /** Stands for a langchain4j-agentic-patterns planner, not on this class path. */
    static class FakeGoalPlanner implements Planner {
        @Override
        public Action nextAction(PlanningContext context) {
            return done();
        }
    }

    static class Suppliers {
        static Planner custom() {
            return new CustomPlanner();
        }

        static Planner builtIn() {
            return new SequentialPlanner();
        }

        static Planner otherObjectFirst() {
            StringBuilder name = new StringBuilder("planner");
            return name.length() > 0 ? new CustomPlanner() : null;
        }

        static Planner factory() {
            return create();
        }

        static Planner create() {
            return new CustomPlanner();
        }

        static Planner pattern() {
            return new FakeGoalPlanner();
        }
    }

    private static final String SUPPLIERS = Suppliers.class.getName() + "#";

    private static PlannerTypeDetector detector() throws Exception {
        return PlannerTypeDetector.of(Index.of(CustomPlanner.class, FakeGoalPlanner.class, SequentialPlanner.class,
                Suppliers.class), PlannerTypeDetectorTest.class.getClassLoader());
    }

    private static PlannerInfo info(String className, PatternType pattern, String method) {
        return new PlannerInfo(className, pattern, Map.of("supplier", SUPPLIERS + method));
    }

    @Test
    void customPlanner() throws Exception {
        assertEquals(info(CustomPlanner.class.getName(), PatternType.CUSTOM, "custom"), detector().detect(SUPPLIERS + "custom"));
    }

    @Test
    void builtInPlanner() throws Exception {
        assertEquals(info(SequentialPlanner.class.getName(), PatternType.CUSTOM, "builtIn"),
                detector().detect(SUPPLIERS + "builtIn"));
    }

    @Test
    void firstConstructedPlanner() throws Exception {
        assertEquals(info(CustomPlanner.class.getName(), PatternType.CUSTOM, "otherObjectFirst"),
                detector().detect(SUPPLIERS + "otherObjectFirst"));
    }

    @Test
    void factoryHidesTheClass() throws Exception {
        assertEquals(info(null, PatternType.CUSTOM, "factory"), detector().detect(SUPPLIERS + "factory"));
    }

    @Test
    void unknownClassOrMethod() throws Exception {
        assertEquals(new PlannerInfo(null, PatternType.CUSTOM, Map.of("supplier", "com.example.Nope#planner")),
                detector().detect("com.example.Nope#planner"));
        assertEquals(info(null, PatternType.CUSTOM, "nope"), detector().detect(SUPPLIERS + "nope"));
    }

    @Test
    void patternPlanner() {
        PlannerTypeDetector detector = new PlannerTypeDetector(
                name -> PlannerTypeDetectorTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class"),
                name -> name.equals(FakeGoalPlanner.class.getName()),
                name -> name.equals(FakeGoalPlanner.class.getName()) ? PatternType.GOAP : null);
        assertEquals(info(FakeGoalPlanner.class.getName(), PatternType.GOAP, "pattern"), detector.detect(SUPPLIERS + "pattern"));
    }
}
