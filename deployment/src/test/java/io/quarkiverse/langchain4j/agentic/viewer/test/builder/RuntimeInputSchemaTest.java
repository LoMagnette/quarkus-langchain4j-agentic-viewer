package io.quarkiverse.langchain4j.agentic.viewer.test.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.RecordComponentInfo;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.InputSchemaGenerator;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.RuntimeInputSchema;

/**
 * The run form schema of a builder system input (runtime, reflection) is the one of a declarative input (build time,
 * Jandex) for the same type.
 */
class RuntimeInputSchemaTest {

    enum Priority {
        LOW,
        HIGH
    }

    record Address(String city, @Nullable String zip) {
    }

    record Person(String name, int age, Address address, Optional<String> nickname, List<Person> friends) {
    }

    /** Like jakarta.annotation.Nullable and jspecify's: visible at runtime. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Nullable {
    }

    /** One component per kind of type the generators know. */
    record Inputs(String text, char letter, int count, long big, double ratio, BigDecimal amount, boolean flag,
            Integer boxed, LocalDate day, UUID id, Priority priority, List<String> words, Set<Integer> numbers,
            Map<String, Double> scores, String[] lines, int[][] grid, Optional<Address> where, Person person,
            List<Map<String, List<Priority>>> nested, Object anything) {
    }

    @Test
    void sameSchemaAsTheBuildTimeGenerator() throws IOException {
        Index index = Index.of(Inputs.class, Address.class, Person.class, Priority.class, Nullable.class);
        InputSchemaGenerator buildTime = new InputSchemaGenerator(index);
        ClassInfo inputs = index.getClassByName(DotName.createSimple(Inputs.class.getName()));

        for (RecordComponentInfo component : inputs.unsortedRecordComponents()) {
            java.lang.reflect.Type runtimeType = java.util.Arrays.stream(Inputs.class.getRecordComponents())
                    .filter(c -> c.getName().equals(component.name())).findFirst().orElseThrow().getGenericType();
            assertEquals(buildTime.schema(component.type()), RuntimeInputSchema.schema(runtimeType), component.name());
        }
    }

    @Test
    void someSchemas() {
        assertEquals("{\"type\":\"string\"}", RuntimeInputSchema.schema(String.class));
        assertEquals("{\"type\":\"string\",\"enum\":[\"LOW\",\"HIGH\"]}", RuntimeInputSchema.schema(Priority.class));
        assertEquals("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"},\"zip\":{\"type\":\"string\"}},"
                + "\"required\":[\"city\"]}", RuntimeInputSchema.schema(Address.class));
    }
}
