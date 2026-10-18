package io.quarkiverse.langchain4j.agentic.viewer.test.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events.PayloadTruncator;
import io.smallrye.mutiny.Multi;

class PayloadTruncatorTest {

    private static final int MAX = 200;
    private final PayloadTruncator truncator = new PayloadTruncator(MAX);

    record Person(String name, List<String> notes, Map<String, Object> extra) {
    }

    /** Not serializable by Jackson: no properties, no default serializer. */
    static class Opaque {
        private final Object self = this;
    }

    @Test
    void smallValuesAreKeptAsJson() {
        PayloadTruncator.Result result = truncator.truncate(new Person("Ada", List.of("a", "b"), Map.of("age", 36)));
        assertFalse(result.truncated());
        assertEquals("{\"name\":\"Ada\",\"notes\":[\"a\",\"b\"],\"extra\":{\"age\":36}}", result.tree().toString());
        assertEquals(Map.of("name", "Ada", "notes", List.of("a", "b"), "extra", Map.of("age", 36)), result.value());
        assertEquals("null", truncator.truncate(null).tree().toString());
    }

    @Test
    void longStringIsCutWithAMarker() {
        String text = "x".repeat(5000);
        PayloadTruncator.Result result = truncator.truncate(text);
        assertTrue(result.truncated());
        String kept = result.tree().asText();
        assertTrue(kept.endsWith("… [truncated, 5000 chars]"), kept);
        assertTrue(result.tree().toString().length() <= MAX, () -> "length " + result.tree().toString().length());
    }

    @Test
    void longCollectionKeepsItsFirstItemsAndCountsTheRest() {
        List<String> items = IntStream.range(0, 100).mapToObj(i -> "item-" + i).toList();
        PayloadTruncator.Result result = truncator.truncate(items);
        assertTrue(result.truncated());
        JsonNode array = result.tree();
        assertEquals("item-0", array.get(0).asText());
        String marker = array.get(array.size() - 1).asText();
        assertEquals("… [" + (100 - (array.size() - 1)) + " more items]", marker);
        assertTrue(array.toString().length() <= MAX, () -> "length " + array.toString().length());
    }

    @Test
    void nestedObjectsAreShrunkInside() {
        Person person = new Person("Ada", List.of("y".repeat(3000)),
                Map.of("history", IntStream.range(0, 50).boxed().toList()));
        PayloadTruncator.Result result = truncator.truncate(person);
        assertTrue(result.truncated());
        JsonNode tree = result.tree();
        assertEquals("Ada", tree.get("name").asText());
        // the long note is cut within the budget left after the name; the extra field no longer fits
        assertEquals("y".repeat(67) + "… [truncated, 3000 chars]", tree.get("notes").get(0).asText(), tree::toString);
        assertEquals(1, tree.get("notes").size());
        assertEquals("[1 more fields]", tree.get("…").asText());
        assertEquals(3, tree.size());
        assertTrue(tree.toString().length() <= MAX, () -> "length " + tree.toString().length());
    }

    @Test
    void mapValuesAreTruncatedOneByOne() {
        Map<String, Object> inputs = truncator.truncateValues(Map.of("short", "hi", "long", "z".repeat(1000)));
        assertEquals("hi", inputs.get("short"), "plain values, not Jackson trees");
        assertTrue(((String) inputs.get("long")).endsWith("… [truncated, 1000 chars]"));
    }

    @Test
    void textPayloads() {
        assertEquals("{\"a\":1}", truncator.truncateText("{\"a\":1}"));
        assertTrue(truncator.truncateText("t".repeat(500)).endsWith("… [truncated, 500 chars]"));
    }

    @Test
    void streamsAndUnserializableValuesBecomeMarkers() {
        assertTrue(truncator.truncate(Multi.createFrom().items(1, 2)).tree().asText().startsWith("<stream "),
                "never subscribed nor stringified");
        assertTrue(truncator.truncate(new Opaque()).tree().asText().contains("not serializable to JSON"));
    }

    /** A getter that fails when Jackson reads it. */
    public static class Failing {
        public String getName() {
            throw new IllegalStateException("not now");
        }

        @Override
        public String toString() {
            throw new IllegalStateException("never stringified");
        }
    }

    @Test
    void cyclicAndFailingValuesBecomeMarkers() {
        Map<String, Object> map = new HashMap<>();
        map.put("me", map);
        List<Object> list = new ArrayList<>();
        list.add(list);
        for (Object value : List.of(map, list, new Failing())) {
            PayloadTruncator.Result result = truncator.truncate(value);
            assertTrue(result.tree().asText().contains("not serializable to JSON"), result.tree()::toString);
        }
        Map<String, Object> inputs = truncator.truncateValues(Map.of("cyclic", map, "fine", "ok"));
        assertEquals("ok", inputs.get("fine"));
        assertTrue(((String) inputs.get("cyclic")).contains("not serializable to JSON"));
    }

    @Test
    void anObjectWithTooManyFieldsKeepsItsFirstOnesAndCountsTheRest() {
        Map<String, Object> wide = new java.util.LinkedHashMap<>();
        IntStream.range(0, 50).forEach(i -> wide.put(String.format("k%02d", i), "value " + i));
        JsonNode tree = truncator.truncate(wide).tree();
        int kept = tree.size() - 1;
        assertEquals("value 0", tree.get("k00").asText());
        assertEquals("[" + (50 - kept) + " more fields]", tree.get("…").asText(), tree::toString);
        assertTrue(tree.toString().length() <= MAX, () -> "length " + tree.toString().length());
    }

    @Test
    void aCutNeverSplitsACharacter() {
        String emojis = "\uD83D\uDE00".repeat(500); // 😀, two UTF-16 chars each
        for (String text : List.of(emojis, "a" + emojis)) {
            String kept = truncator.truncate(text).tree().asText();
            String content = kept.substring(0, kept.indexOf("… [truncated"));
            assertTrue(!content.isEmpty() && !Character.isHighSurrogate(content.charAt(content.length() - 1)),
                    () -> "cut inside a character: " + content.length());
            assertTrue(truncator.truncateText(text).codePoints().noneMatch(c -> Character.isSurrogate((char) c)));
        }
    }

    @Test
    void theLimitIsAtLeastEightyCharacters() {
        PayloadTruncator tiny = new PayloadTruncator(40);
        String kept = tiny.truncate("x".repeat(500)).tree().toString();
        assertTrue(kept.length() > 40 && kept.length() <= 80, () -> "length " + kept.length() + ": " + kept);
    }
}
