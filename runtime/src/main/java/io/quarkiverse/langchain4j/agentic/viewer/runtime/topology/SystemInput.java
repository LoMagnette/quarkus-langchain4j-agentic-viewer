package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

/**
 * An input of an agentic system, used to generate its run form.
 *
 * @param name parameter name, i.e. the AgenticScope key it is stored under
 * @param javaType Java type name (generic types included, e.g. {@code java.util.List<java.lang.String>})
 * @param jsonSchema JSON schema of the value, as JSON text
 */
public record SystemInput(String name, String javaType, String jsonSchema) {
}
