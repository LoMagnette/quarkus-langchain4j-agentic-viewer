package io.quarkiverse.langchain4j.agentic.viewer.test.patterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Function;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.patterns.bdi.BDIPlanner;
import dev.langchain4j.agentic.patterns.bdi.Desire;
import dev.langchain4j.agentic.planner.Planner;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.topology.PlannerTypeDetector;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PlannerDesire;

/**
 * The desires of a BDI {@code @PlannerSupplier} are read from its bytecode when it declares them with literals.
 */
class DesireReaderTest {

    interface Fixer {
    }

    interface Writer {
    }

    static final int PRIORITY = 7;

    static class Suppliers {
        static Planner keys() {
            return new BDIPlanner(List.of(
                    Desire.of("fix", 10, "bug", "patch", Fixer.class),
                    Desire.of("announce", 5, "patch", "news", Fixer.class, Writer.class)), 20);
        }

        static Planner predicates() {
            return new BDIPlanner(List.of(Desire.of("fix", 1, scope -> scope.hasState("bug"),
                    scope -> !scope.hasState("open"), Fixer.class)));
        }

        static Planner constructor() {
            return new BDIPlanner(List.of(new Desire("fix", 2, scope -> true, scope -> false, List.of(Fixer.class, Writer.class))));
        }

        static Planner constantPriority() {
            return new BDIPlanner(List.of(Desire.of("fix", PRIORITY, "bug", "patch", Fixer.class)));
        }

        static Planner helper() {
            return new BDIPlanner(desires());
        }

        static List<Desire> desires() {
            return List.of(Desire.of("fix", 1, "bug", "patch", Fixer.class));
        }

        static Planner computedName() {
            String name = "fi" + "x".repeat(1);
            return new BDIPlanner(List.of(Desire.of(name, 1, "bug", "patch", Fixer.class)));
        }
    }

    private static final String SUPPLIERS = Suppliers.class.getName() + "#";
    private static final Function<String, String> NODE_IDS = className -> className.equals(Fixer.class.getName()) ? "plan/Fixer"
            : className.equals(Writer.class.getName()) ? "plan/Writer" : null;

    private static PlannerTypeDetector detector() throws Exception {
        return PlannerTypeDetector.of(Index.of(Suppliers.class), DesireReaderTest.class.getClassLoader());
    }

    @Test
    void literalKeys() throws Exception {
        assertEquals(PatternType.BDI, detector().detect(SUPPLIERS + "keys").patternType());
        assertEquals(List.of(new PlannerDesire("fix", 10, "bug", "patch", List.of("plan/Fixer")),
                new PlannerDesire("announce", 5, "patch", "news", List.of("plan/Fixer", "plan/Writer"))),
                detector().desires(SUPPLIERS + "keys", NODE_IDS));
    }

    @Test
    void predicatesLeaveTheConditionsUnknown() throws Exception {
        assertEquals(List.of(new PlannerDesire("fix", 1, null, null, List.of("plan/Fixer"))),
                detector().desires(SUPPLIERS + "predicates", NODE_IDS));
    }

    @Test
    void canonicalConstructorWithAListOfAgents() throws Exception {
        assertEquals(List.of(new PlannerDesire("fix", 2, null, null, List.of("plan/Fixer", "plan/Writer"))),
                detector().desires(SUPPLIERS + "constructor", NODE_IDS));
    }

    @Test
    void aCompileTimeConstantPriorityIsKnown() throws Exception {
        // a compile-time constant is inlined: only a field read at run time would be unknown
        assertEquals(List.of(new PlannerDesire("fix", PRIORITY, "bug", "patch", List.of("plan/Fixer"))),
                detector().desires(SUPPLIERS + "constantPriority", NODE_IDS));
    }

    @Test
    void desiresBuiltElsewhereOrNotLiteralAreUnknown() throws Exception {
        assertNull(detector().desires(SUPPLIERS + "helper", NODE_IDS), "built in another method");
        assertNull(detector().desires(SUPPLIERS + "computedName", NODE_IDS), "a name computed at run time");
        assertNull(detector().desires(SUPPLIERS + "keys", className -> null), "agents that are no sub-agents");
        assertNull(detector().desires(SUPPLIERS + "nope", NODE_IDS), "no such supplier");
    }
}
