package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The JSON schema of a run form input of a builder system (T5.6), from its Java type: same rules and output as the
 * build-time InputSchemaGenerator of the declarative systems, through reflection.
 */
public final class RuntimeInputSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NULLABLE = "Nullable";

    private static final Set<String> INTEGERS = Set.of("byte", "short", "int", "long", "java.lang.Byte",
            "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.math.BigInteger");
    private static final Set<String> NUMBERS = Set.of("float", "double", "java.lang.Float", "java.lang.Double",
            "java.math.BigDecimal", "java.lang.Number");
    private static final Set<String> BOOLEANS = Set.of("boolean", "java.lang.Boolean");
    private static final Set<String> STRINGS = Set.of("java.lang.String", "java.lang.CharSequence");
    private static final Set<String> CHARS = Set.of("char", "java.lang.Character");
    private static final Map<String, String> FORMATS = Map.of(
            "java.time.LocalDate", "date",
            "java.time.LocalTime", "time",
            "java.time.LocalDateTime", "date-time",
            "java.time.OffsetDateTime", "date-time",
            "java.time.ZonedDateTime", "date-time",
            "java.time.Instant", "date-time",
            "java.time.Duration", "duration",
            "java.util.UUID", "uuid",
            "java.net.URI", "uri",
            "java.net.URL", "uri");
    private static final Set<String> COLLECTIONS = Set.of("java.util.Collection", "java.lang.Iterable",
            "java.util.List", "java.util.ArrayList", "java.util.LinkedList");
    private static final Set<String> SETS = Set.of("java.util.Set", "java.util.HashSet", "java.util.LinkedHashSet",
            "java.util.SortedSet", "java.util.TreeSet");
    private static final Set<String> MAPS = Set.of("java.util.Map", "java.util.HashMap", "java.util.LinkedHashMap",
            "java.util.SortedMap", "java.util.TreeMap");

    private RuntimeInputSchema() {
    }

    /**
     * @return the JSON schema of a value of this type, as JSON text
     */
    public static String schema(Type type) {
        try {
            return MAPPER.writeValueAsString(schemaOf(type, new HashSet<>()));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** Required unless an {@code Optional} or annotated {@code @Nullable} (from any package); primitives always. */
    static boolean required(Type type, AnnotatedElement annotated) {
        Class<?> raw = raw(type);
        if (raw != null && raw.isPrimitive()) {
            return true;
        }
        if (raw == Optional.class) {
            return false;
        }
        return annotated == null || Arrays.stream(annotated.getAnnotations()).noneMatch(RuntimeInputSchema::isNullable);
    }

    private static boolean isNullable(Annotation annotation) {
        String name = annotation.annotationType().getName();
        return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1).equals(NULLABLE);
    }

    /** {@code visiting}: records on the current path, to stop on recursive types */
    private static Map<String, Object> schemaOf(Type type, Set<Class<?>> visiting) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (type instanceof GenericArrayType array) {
            schema.put("type", "array");
            schema.put("items", schemaOf(array.getGenericComponentType(), visiting));
            return schema;
        }
        Class<?> raw = raw(type);
        if (raw == null) {
            return schema; // type variables, wildcards: anything
        }
        if (raw.isArray()) {
            schema.put("type", "array");
            schema.put("items", schemaOf(raw.getComponentType(), visiting));
            return schema;
        }
        String name = raw.getName();
        if (BOOLEANS.contains(name)) {
            schema.put("type", "boolean");
        } else if (INTEGERS.contains(name)) {
            schema.put("type", "integer");
        } else if (NUMBERS.contains(name)) {
            schema.put("type", "number");
        } else if (STRINGS.contains(name)) {
            schema.put("type", "string");
        } else if (CHARS.contains(name)) {
            schema.put("type", "string");
            schema.put("minLength", 1);
            schema.put("maxLength", 1);
        } else if (FORMATS.containsKey(name)) {
            schema.put("type", "string");
            schema.put("format", FORMATS.get(name));
        } else if (raw == Optional.class) {
            return schemaOf(typeArgument(type, 0), visiting);
        } else if (COLLECTIONS.contains(name) || SETS.contains(name)) {
            schema.put("type", "array");
            schema.put("items", schemaOf(typeArgument(type, 0), visiting));
            if (SETS.contains(name)) {
                schema.put("uniqueItems", true);
            }
        } else if (MAPS.contains(name)) {
            schema.put("type", "object");
            // JSON object keys are strings: other key types are left to the deserializer
            schema.put("additionalProperties", schemaOf(typeArgument(type, 1), visiting));
        } else {
            return classSchema(raw, visiting);
        }
        return schema;
    }

    private static Map<String, Object> classSchema(Class<?> type, Set<Class<?>> visiting) {
        if (!visiting.add(type)) {
            return new LinkedHashMap<>(); // recursive: anything
        }
        try {
            Map<String, Object> schema = new LinkedHashMap<>();
            if (type.isEnum()) {
                schema.put("type", "string");
                List<String> constants = new ArrayList<>();
                for (Object constant : type.getEnumConstants()) {
                    constants.add(((Enum<?>) constant).name());
                }
                schema.put("enum", constants);
            } else if (type.isRecord()) {
                Map<String, Object> properties = new LinkedHashMap<>();
                List<String> required = new ArrayList<>();
                for (RecordComponent component : type.getRecordComponents()) {
                    properties.put(component.getName(), schemaOf(component.getGenericType(), visiting));
                    if (required(component.getGenericType(), component)) {
                        required.add(component.getName());
                    }
                }
                schema.put("type", "object");
                schema.put("properties", properties);
                if (!required.isEmpty()) {
                    schema.put("required", required);
                }
            }
            return schema;
        } finally {
            visiting.remove(type);
        }
    }

    private static Class<?> raw(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> c) {
            return c;
        }
        return null;
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized && index < parameterized.getActualTypeArguments().length) {
            return parameterized.getActualTypeArguments()[index];
        }
        return Object.class;
    }
}
