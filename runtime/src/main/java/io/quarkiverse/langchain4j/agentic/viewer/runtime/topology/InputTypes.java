package io.quarkiverse.langchain4j.agentic.viewer.runtime.topology;

import java.util.Map;
import java.util.Set;

/**
 * The Java types the run form knows, by name: the build-time input schema generator (Jandex) and the runtime one of
 * builder systems (reflection) share them, so declarative and builder forms agree.
 */
public final class InputTypes {

    /** Simple name of the annotations making an input optional, from any package (jakarta, jspecify...). */
    public static final String NULLABLE = "Nullable";

    public static final Set<String> INTEGERS = Set.of("byte", "short", "int", "long", "java.lang.Byte",
            "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.math.BigInteger");
    public static final Set<String> NUMBERS = Set.of("float", "double", "java.lang.Float", "java.lang.Double",
            "java.math.BigDecimal", "java.lang.Number");
    public static final Set<String> BOOLEANS = Set.of("boolean", "java.lang.Boolean");
    public static final Set<String> STRINGS = Set.of("java.lang.String", "java.lang.CharSequence");
    public static final Set<String> CHARS = Set.of("char", "java.lang.Character");
    public static final Map<String, String> FORMATS = Map.of(
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
    public static final Set<String> COLLECTIONS = Set.of("java.util.Collection", "java.lang.Iterable",
            "java.util.List", "java.util.ArrayList", "java.util.LinkedList");
    public static final Set<String> SETS = Set.of("java.util.Set", "java.util.HashSet", "java.util.LinkedHashSet",
            "java.util.SortedSet", "java.util.TreeSet");
    public static final Set<String> MAPS = Set.of("java.util.Map", "java.util.HashMap", "java.util.LinkedHashMap",
            "java.util.SortedMap", "java.util.TreeMap");

    private InputTypes() {
    }

    /** {@code jakarta.annotation.Nullable}, {@code org.jspecify.annotations.Nullable}, a nested {@code Outer$Nullable}... */
    public static boolean isNullable(String annotationClassName) {
        return annotationClassName.substring(Math.max(annotationClassName.lastIndexOf('.'),
                annotationClassName.lastIndexOf('$')) + 1).equals(NULLABLE);
    }
}
