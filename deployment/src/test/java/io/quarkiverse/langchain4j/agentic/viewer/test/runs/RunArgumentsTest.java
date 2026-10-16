package io.quarkiverse.langchain4j.agentic.viewer.test.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.runs.CdiRunInvoker;

/**
 * Run form JSON (as decoded by the JSON-RPC layer: maps, lists, strings, numbers, booleans) converted to the root
 * method parameter types.
 */
class RunArgumentsTest {

    enum Mood {
        HAPPY,
        SAD
    }

    record Address(String street, Integer zip) {
    }

    record Person(String name, int age, Mood mood, Address address, List<Address> previous) {
    }

    interface Root {
        @Agent
        String run(@V("text") String text, @V("count") int count, @V("ratio") double ratio, @V("flag") boolean flag,
                @V("mood") Mood mood, @V("person") Person person, @V("tags") List<String> tags,
                @V("ids") Set<Integer> ids, @V("scores") Map<String, Double> scores, @V("day") LocalDate day,
                @V("note") Optional<String> note, @MemoryId String memory, AgenticScope scope, String unnamed);
    }

    private static final Method RUN = Root.class.getMethods()[0];

    private static Map<String, Object> form() {
        return Map.ofEntries(
                Map.entry("text", "hello"),
                Map.entry("count", 3),
                Map.entry("ratio", 0.5),
                Map.entry("flag", true),
                Map.entry("mood", "SAD"),
                Map.entry("person", Map.of("name", "Ada", "age", 36, "mood", "HAPPY",
                        "address", Map.of("street", "Main St", "zip", 1000),
                        "previous", List.of(Map.of("street", "Old St")))),
                Map.entry("tags", List.of("a", "b")),
                Map.entry("ids", List.of(1, 2, 2)),
                Map.entry("scores", Map.of("x", 1.5)),
                Map.entry("day", "2026-10-16"),
                Map.entry("note", "a note"),
                Map.entry("unnamed", "by parameter name"));
    }

    @Test
    void everySupportedType() {
        Object[] arguments = CdiRunInvoker.arguments(RUN, form());

        assertEquals("hello", arguments[0]);
        assertEquals(3, arguments[1]);
        assertEquals(0.5, arguments[2]);
        assertEquals(true, arguments[3]);
        assertEquals(Mood.SAD, arguments[4]);
        assertEquals(new Person("Ada", 36, Mood.HAPPY, new Address("Main St", 1000), List.of(new Address("Old St", null))),
                arguments[5]);
        assertEquals(List.of("a", "b"), arguments[6]);
        assertEquals(Set.of(1, 2), arguments[7]);
        assertEquals(Map.of("x", 1.5), arguments[8]);
        assertEquals(LocalDate.of(2026, 10, 16), arguments[9]);
        assertEquals(Optional.of("a note"), arguments[10]);
        assertEquals(36, ((String) arguments[11]).length(), "a fresh memory id");
        assertNull(arguments[12], "the scope is provided by langchain4j");
        assertEquals("by parameter name", arguments[13]);
    }

    @Test
    void missingObjectValuesAreNull() {
        Map<String, Object> form = new HashMap<>(form());
        form.remove("text");
        form.remove("person");
        Object[] arguments = CdiRunInvoker.arguments(RUN, form);
        assertNull(arguments[0]);
        assertNull(arguments[5]);
    }

    @Test
    void missingPrimitiveNamesTheInput() {
        Map<String, Object> form = new HashMap<>(form());
        form.remove("count");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CdiRunInvoker.arguments(RUN, form));
        assertEquals("Missing value for input 'count'", error.getMessage());
    }

    @Test
    void conversionErrorNamesTheInputAndType() {
        Map<String, Object> form = new HashMap<>(form());
        form.put("mood", "ANGRY");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CdiRunInvoker.arguments(RUN, form));
        assertTrue(error.getMessage().startsWith("Cannot convert input 'mood' to " + Mood.class.getName() + ": "),
                error.getMessage());

        form.put("mood", "SAD");
        form.put("count", "three");
        error = assertThrows(IllegalArgumentException.class, () -> CdiRunInvoker.arguments(RUN, form));
        assertTrue(error.getMessage().startsWith("Cannot convert input 'count' to int: "), error.getMessage());
    }

    @Test
    void givenMemoryIdIsKept() {
        Map<String, Object> form = new HashMap<>(form());
        form.put("memory", "session-1");
        assertEquals("session-1", CdiRunInvoker.arguments(RUN, form)[11]);
    }
}
