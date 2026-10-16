package io.quarkiverse.langchain4j.agentic.viewer.test.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgentDescriptor;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.AgenticAnnotationScanner;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;

/**
 * JSON schemas of the parameters of an agent method, one test per supported type.
 */
class InputSchemaGeneratorTest {

    /** Any annotation named Nullable makes a parameter or record component optional. */
    @interface Nullable {
    }

    enum Mood {
        HAPPY,
        SAD
    }

    record Address(String street, @Nullable Integer zip, Optional<String> note) {
    }

    record Person(String name, int age, Mood mood, Address address, List<Address> previous) {
    }

    record TreeNode(String label, List<TreeNode> children) {
    }

    /** Not a record: its shape is unknown to the build. */
    static class Bean {
        public String value;
    }

    interface Everything {
        @Agent
        String run(@V("text") String text, @V("count") int count, @V("big") Long big, @V("ratio") double ratio,
                @V("flag") Boolean flag, @V("initial") char initial, @V("mood") Mood mood, @V("person") Person person,
                @V("tags") List<String> tags, @V("ids") Set<Integer> ids, @V("scores") Map<String, Double> scores,
                @V("lines") String[] lines, @V("grid") int[][] grid, @V("day") Optional<LocalDate> day,
                @V("note") @Nullable String note, @V("id") UUID id, @V("tree") TreeNode tree, @V("bean") Bean bean,
                @V("anything") Object anything, AgenticScope scope, @MemoryId String memory);
    }

    private static Map<String, SystemInput> inputs;

    @BeforeAll
    static void scan() throws Exception {
        Index index = Index.of(Everything.class, Mood.class, Address.class, Person.class, TreeNode.class, Bean.class,
                Nullable.class);
        AgentDescriptor descriptor = new AgenticAnnotationScanner().scan(index).get(0);
        inputs = descriptor.inputs().stream().collect(Collectors.toMap(SystemInput::name, Function.identity()));
        assertEquals(descriptor.inputKeys(), descriptor.inputs().stream().map(SystemInput::name).toList(),
                "same keys, same order; the scope and the memory id are not inputs");
    }

    private static String schema(String name) {
        return inputs.get(name).jsonSchema();
    }

    @Test
    void primitivesAndWrappers() {
        assertEquals("{\"type\":\"integer\"}", schema("count"));
        assertEquals("{\"type\":\"integer\"}", schema("big"));
        assertEquals("{\"type\":\"number\"}", schema("ratio"));
        assertEquals("{\"type\":\"boolean\"}", schema("flag"));
        assertEquals("{\"type\":\"string\",\"minLength\":1,\"maxLength\":1}", schema("initial"));
        assertEquals("int", inputs.get("count").javaType());
    }

    @Test
    void strings() {
        assertEquals("{\"type\":\"string\"}", schema("text"));
        assertEquals("{\"type\":\"string\",\"format\":\"uuid\"}", schema("id"));
    }

    @Test
    void enums() {
        assertEquals("{\"type\":\"string\",\"enum\":[\"HAPPY\",\"SAD\"]}", schema("mood"));
    }

    @Test
    void records() {
        String address = "{\"type\":\"object\",\"properties\":{\"street\":{\"type\":\"string\"},"
                + "\"zip\":{\"type\":\"integer\"},\"note\":{\"type\":\"string\"}},\"required\":[\"street\"]}";
        assertEquals("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\"},"
                + "\"mood\":{\"type\":\"string\",\"enum\":[\"HAPPY\",\"SAD\"]},\"address\":" + address + ","
                + "\"previous\":{\"type\":\"array\",\"items\":" + address + "}},"
                + "\"required\":[\"name\",\"age\",\"mood\",\"address\",\"previous\"]}", schema("person"));
    }

    @Test
    void recursiveRecordStopsAtTheCycle() {
        assertEquals("{\"type\":\"object\",\"properties\":{\"label\":{\"type\":\"string\"},"
                + "\"children\":{\"type\":\"array\",\"items\":{}}},\"required\":[\"label\",\"children\"]}", schema("tree"));
    }

    @Test
    void listsSetsAndArrays() {
        assertEquals("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}", schema("tags"));
        assertEquals("{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"uniqueItems\":true}", schema("ids"));
        assertEquals("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}", schema("lines"));
        assertEquals("{\"type\":\"array\",\"items\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}}",
                schema("grid"));
        assertEquals("java.util.List<java.lang.String>", inputs.get("tags").javaType());
    }

    @Test
    void maps() {
        assertEquals("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"number\"}}", schema("scores"));
    }

    @Test
    void unknownTypesAcceptAnyJson() {
        assertEquals("{}", schema("bean"));
        assertEquals("{}", schema("anything"));
    }

    @Test
    void requiredUnlessOptionalOrNullable() {
        assertEquals("{\"type\":\"string\",\"format\":\"date\"}", schema("day"), "Optional<T> has the schema of T");
        assertEquals(Set.of("day", "note"), inputs.values().stream().filter(input -> !input.required())
                .map(SystemInput::name).collect(Collectors.toSet()));
    }
}
