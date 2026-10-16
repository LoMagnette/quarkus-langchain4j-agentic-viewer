package io.quarkiverse.langchain4j.agentic.viewer.deployment.scan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.RecordComponentInfo;
import org.jboss.jandex.Type;

/**
 * JSON schemas of agent method parameters, from their Jandex types, for the run form.
 * <p>
 * Covers primitives and their wrappers, strings, enums, records, arrays, collections, maps with string keys,
 * {@code Optional} and common date, time and UUID types. Anything else (a class that is not a record, a type that is
 * not in the index, a type variable, a recursive record) gets the empty schema, which accepts any JSON value.
 */
public class InputSchemaGenerator {

    private static final DotName OPTIONAL = DotName.createSimple("java.util.Optional");
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

    private final IndexView index;

    public InputSchemaGenerator(IndexView index) {
        this.index = index;
    }

    /**
     * @return the JSON schema of a value of this type, as JSON text
     */
    public String schema(Type type) {
        return Json.write(schemaOf(type, new HashSet<>()));
    }

    /**
     * A parameter (or record component) is required unless it is an {@code Optional} or annotated
     * {@code @Nullable} (from any package); primitives are always required.
     */
    public static boolean required(Type type, AnnotationTarget annotated) {
        if (type.kind() == Type.Kind.PRIMITIVE) {
            return true;
        }
        if (type.name().equals(OPTIONAL)) {
            return false;
        }
        return annotated.declaredAnnotations().stream().noneMatch(annotation -> isNullable(annotation.name()))
                && type.annotations().stream().noneMatch(annotation -> isNullable(annotation.name()));
    }

    /** Matches jakarta.annotation.Nullable, org.jspecify.annotations.Nullable, a nested Outer$Nullable... */
    private static boolean isNullable(DotName annotation) {
        String name = annotation.toString();
        return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1).equals(NULLABLE);
    }

    /** {@code visiting}: records on the current path, to stop on recursive types */
    private Map<String, Object> schemaOf(Type type, Set<DotName> visiting) {
        Map<String, Object> schema = new LinkedHashMap<>();
        switch (type.kind()) {
            case PRIMITIVE, CLASS, PARAMETERIZED_TYPE -> {
                String name = type.name().toString();
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
                } else if (name.equals(OPTIONAL.toString())) {
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
                    return classSchema(type.name(), visiting);
                }
            }
            case ARRAY -> {
                // int[][] is an array of int[]
                int dimensions = type.asArrayType().dimensions();
                Type constituent = type.asArrayType().constituent();
                schema.put("type", "array");
                schema.put("items", schemaOf(dimensions > 1 ? ArrayType.create(constituent, dimensions - 1) : constituent,
                        visiting));
            }
            default -> {
                // type variables, wildcards, void: anything
            }
        }
        return schema;
    }

    private Map<String, Object> classSchema(DotName name, Set<DotName> visiting) {
        ClassInfo info = index.getClassByName(name);
        if (info == null || !visiting.add(name)) {
            return new LinkedHashMap<>(); // not indexed, or recursive: anything
        }
        try {
            Map<String, Object> schema = new LinkedHashMap<>();
            if (info.isEnum()) {
                schema.put("type", "string");
                schema.put("enum", info.enumConstants().stream().map(FieldInfo::name).toList());
            } else if (info.isRecord()) {
                Map<String, Object> properties = new LinkedHashMap<>();
                List<String> required = new ArrayList<>();
                for (RecordComponentInfo component : info.unsortedRecordComponents()) {
                    properties.put(component.name(), schemaOf(component.type(), visiting));
                    if (required(component.type(), component)) {
                        required.add(component.name());
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
            visiting.remove(name);
        }
    }

    private static Type typeArgument(Type type, int index) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            List<Type> arguments = type.asParameterizedType().arguments();
            if (index < arguments.size()) {
                return arguments.get(index);
            }
        }
        return Type.create(DotName.createSimple("java.lang.Object"), Type.Kind.CLASS);
    }

    /** Minimal JSON writer for the schema maps: maps, lists, strings, numbers and booleans. */
    static final class Json {

        private Json() {
        }

        static String write(Object value) {
            StringBuilder out = new StringBuilder();
            write(value, out);
            return out.toString();
        }

        private static void write(Object value, StringBuilder out) {
            if (value instanceof Map<?, ?> map) {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    string(entry.getKey().toString(), out);
                    out.append(':');
                    write(entry.getValue(), out);
                }
                out.append('}');
            } else if (value instanceof List<?> list) {
                out.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    write(list.get(i), out);
                }
                out.append(']');
            } else if (value instanceof String string) {
                string(string, out);
            } else {
                out.append(value); // numbers and booleans
            }
        }

        private static void string(String value, StringBuilder out) {
            out.append('"');
            for (char c : value.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }
    }
}
