package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Turns event payload values into bounded, plain JSON values (maps, lists, strings, numbers, booleans, null): large
 * scope values or outputs would slow the Dev UI and use memory. Plain values, not Jackson trees: the Dev UI serializer
 * writes a JsonNode as a bean.
 * <p>
 * A value becomes a Jackson tree; when its JSON text is longer than the limit, strings are cut with a marker, and arrays
 * and objects keep their first entries within the budget followed by a marker. Streaming outputs and values Jackson
 * cannot convert (failing getters, cycles, a map or list containing itself) are replaced by a marker, never by their
 * {@code toString()} (a streaming response would block). It never throws: an event must not be lost for its payload.
 */
public final class PayloadTruncator {

    /** Payload values bigger than this many characters of JSON are truncated, by default. */
    public static final int DEFAULT_MAX_SIZE = 2048;

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final List<String> STREAM_TYPES = List.of("dev.langchain4j.service.TokenStream",
            "io.smallrye.mutiny.Multi", "java.util.concurrent.Flow$Publisher", "java.util.stream.Stream");
    /** Room kept for the marker closing a truncated container. */
    private static final int MARKER_ROOM = 40;

    private final int maxSize;

    public PayloadTruncator(int maxSize) {
        this.maxSize = Math.max(MARKER_ROOM * 2, maxSize);
    }

    /**
     * @param tree the bounded value as a Jackson tree
     * @param truncated whether something was cut
     */
    public record Result(JsonNode tree, boolean truncated) {

        /** @return the bounded value as plain Java values, for events */
        public Object value() {
            return plain(tree);
        }
    }

    public Result truncate(Object value) {
        JsonNode tree = toTree(value);
        if (tree.toString().length() <= maxSize) {
            return new Result(tree, false);
        }
        return new Result(shrink(tree, maxSize), true);
    }

    /** Truncates every value of a map (e.g. agent inputs), within the limit for each value. */
    public Map<String, Object> truncateValues(Map<String, Object> values) {
        if (values == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            values.forEach((key, value) -> result.put(key, truncate(value).value()));
        } catch (RuntimeException e) { // e.g. a map of the application changed meanwhile
            result.put("…", "<" + e.getClass().getName() + " while reading the values>");
        }
        return result;
    }

    /** A string payload (tool arguments or result), cut with a marker beyond the limit. */
    public String truncateText(String text) {
        return text == null || text.length() <= maxSize ? text : cut(text, maxSize);
    }

    private static Object plain(JsonNode tree) {
        return tree == null || tree.isNull() ? null : MAPPER.convertValue(tree, Object.class);
    }

    private static JsonNode toTree(Object value) {
        if (value == null) {
            return NODES.nullNode();
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        if (isStream(value.getClass())) {
            return TextNode.valueOf("<stream " + value.getClass().getName() + ">");
        }
        try {
            return MAPPER.valueToTree(value);
        } catch (RuntimeException | StackOverflowError e) {
            // Jackson reports bean cycles, but recurses without end into a map or list containing itself
            return TextNode.valueOf("<" + value.getClass().getName() + ", not serializable to JSON>");
        }
    }

    private static boolean isStream(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (STREAM_TYPES.contains(c.getName())) {
                return true;
            }
            for (Class<?> i : c.getInterfaces()) {
                if (isStream(i)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A tree whose JSON text fits in about {@code budget} characters. */
    private static JsonNode shrink(JsonNode node, int budget) {
        if (node.isTextual()) {
            return TextNode.valueOf(cut(node.asText(), budget));
        }
        if (node.isArray()) {
            ArrayNode array = NODES.arrayNode();
            int used = 2;
            int index = 0;
            for (JsonNode element : node) {
                JsonNode kept = fit(element, budget - used - MARKER_ROOM);
                if (kept == null) {
                    break;
                }
                array.add(kept);
                used += kept.toString().length() + 1;
                index++;
            }
            if (index < node.size()) {
                array.add(TextNode.valueOf("… [" + (node.size() - index) + " more items]"));
            }
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = NODES.objectNode();
            int used = 2;
            int kept = 0;
            for (Iterator<Map.Entry<String, JsonNode>> fields = node.fields(); fields.hasNext();) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = fit(field.getValue(), budget - used - field.getKey().length() - 4 - MARKER_ROOM);
                if (value == null) {
                    break;
                }
                object.set(field.getKey(), value);
                used += field.getKey().length() + 4 + value.toString().length();
                kept++;
            }
            if (kept < node.size()) {
                object.put("…", "[" + (node.size() - kept) + " more fields]");
            }
            return object;
        }
        return node; // numbers, booleans, null
    }

    /** The node itself, or a shrunk copy, within the budget; null when not even a shrunk copy fits. */
    private static JsonNode fit(JsonNode node, int budget) {
        if (node.toString().length() <= budget) {
            return node;
        }
        if (budget < MARKER_ROOM || !(node.isTextual() || node.isContainerNode())) {
            return null;
        }
        return shrink(node, budget);
    }

    private static String cut(String text, int budget) {
        String marker = "… [truncated, " + text.length() + " chars]";
        int keep = Math.min(Math.max(0, budget - marker.length() - 2), text.length());
        if (keep > 0 && Character.isHighSurrogate(text.charAt(keep - 1))) {
            keep--; // never half of a character (an emoji is two UTF-16 chars)
        }
        return text.substring(0, keep) + marker;
    }
}
