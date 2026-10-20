package io.quarkiverse.langchain4j.agentic.viewer.test.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

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
        assertEquals("y".repeat(68) + "… [truncated, 3000 chars]", tree.get("notes").get(0).asText(), tree::toString);
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
    void aHugeValueIsNotWalkedToTheEnd() {
        int[] read = { 0 };
        List<String> huge = new AbstractList<>() {
            @Override
            public String get(int index) {
                read[0]++;
                return "line " + index;
            }

            @Override
            public int size() {
                return 5_000_000;
            }
        };
        JsonNode array = truncator.truncate(huge).tree();
        assertEquals("line 0", array.get(0).asText());
        assertEquals("… [more items]", array.get(array.size() - 1).asText(), "too many to count");
        assertTrue(array.toString().length() <= MAX, () -> "length " + array.toString().length());
        assertTrue(read[0] < 20_000, () -> read[0] + " items read");
    }

    @Test
    void aHugeNestedValueStopsInsideAndEndsItsOpenContainers() {
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("title", "load");
        report.put("rows", IntStream.range(0, 200_000).mapToObj(i -> Map.of("n", i, "label", "row " + i)).toList());
        PayloadTruncator.Result result = truncator.truncate(report);
        assertTrue(result.truncated());
        JsonNode tree = result.tree();
        assertEquals("load", tree.get("title").asText());
        JsonNode rows = tree.get("rows");
        assertEquals("… [more items]", rows.get(rows.size() - 1).asText(), tree::toString);
        assertTrue(tree.toString().length() <= MAX, () -> "length " + tree.toString().length() + ": " + tree);
    }

    @Test
    void primitiveArraysAndJsonTreesAreBoundedToo() {
        JsonNode numbers = truncator.truncate(IntStream.range(0, 100).toArray()).tree();
        assertEquals(0, numbers.get(0).asInt());
        assertTrue(numbers.get(numbers.size() - 1).asText().matches("… \\[\\d+ more items]"), numbers::toString);
        assertTrue(numbers.toString().length() <= MAX, () -> "length " + numbers.toString().length());

        JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("text", "w".repeat(900));
        PayloadTruncator.Result result = truncator.truncate(node);
        assertTrue(result.truncated());
        assertTrue(result.tree().get("text").asText().endsWith("… [truncated, 900 chars]"));
        assertTrue(result.tree().toString().length() <= MAX, () -> "length " + result.tree().toString().length());
    }

    @Test
    void charArraysAndNullStringsAreWrittenAsTheirStrings() {
        String kept = truncator.truncate("q".repeat(5000).toCharArray()).tree().asText();
        assertTrue(kept.startsWith("qqq") && kept.endsWith("… [truncated, 5000 chars]"), kept);
        assertEquals(1, kept.split("… \\[truncated").length - 1, "one marker");
        assertEquals("[\"a\",null]", truncator.truncate(new String[] { "a", null }).tree().toString());
    }

    @Test
    void hugeNumbersAndBinariesStayWithinTheLimitWherever() {
        BigInteger huge = BigInteger.TEN.pow(10_000);
        PayloadTruncator.Result root = truncator.truncate(huge);
        assertTrue(root.truncated());
        assertEquals("<number of about 10001 digits>", root.tree().asText());

        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("label", "l".repeat(60));
        fields.put("count", BigInteger.TEN.pow(150)); // its text fits the limit, not the room left
        fields.put("amount", new BigDecimal(BigInteger.TEN.pow(5_000), 2));
        fields.put("data", new byte[5_000]);
        JsonNode tree = truncator.truncate(fields).tree();
        assertTrue(tree.get("count").asText().matches("1000+… \\[truncated, 151 chars]"), tree::toString);
        // the fields written always get a value
        tree.fields().forEachRemaining(field -> assertTrue(field.getValue().isTextual(), tree::toString));
        assertTrue(tree.toString().length() <= MAX, () -> "length " + tree.toString().length() + ": " + tree);

        JsonNode inArray = truncator.truncate(List.of("a".repeat(150), BigInteger.TEN.pow(40), new byte[300])).tree();
        assertTrue(inArray.toString().length() <= MAX, () -> "length " + inArray.toString().length() + ": " + inArray);
        assertEquals("<300 bytes>", truncator.truncate(Map.of("data", new byte[300])).tree().get("data").asText());
        assertEquals("AQID", truncator.truncate(new byte[] { 1, 2, 3 }).tree().asText(), "base64 when it fits");
    }

    /** Written by a custom serializer as UTF-8 bytes: "é" is two bytes. */
    @JsonSerialize(using = Utf8Serializer.class)
    record Utf8Text(byte[] bytes) {
    }

    static class Utf8Serializer extends StdSerializer<Utf8Text> {
        Utf8Serializer() {
            super(Utf8Text.class);
        }

        @Override
        public void serialize(Utf8Text value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeUTF8String(value.bytes(), 0, value.bytes().length);
        }
    }

    @Test
    void utf8StringsAreCutWithTheirLengthInChars() {
        String kept = truncator.truncate(new Utf8Text("é".repeat(300).getBytes(StandardCharsets.UTF_8))).tree().asText();
        assertTrue(kept.startsWith("ééé") && kept.endsWith("… [truncated, 300 chars]"), kept);
        assertTrue(kept.length() + 2 <= MAX, kept);
        // far longer than the limit: neither decoded nor counted, its bytes are told
        byte[] bytes = "é".repeat(50_000).getBytes(StandardCharsets.UTF_8);
        String huge = truncator.truncate(new Utf8Text(bytes)).tree().asText();
        assertTrue(huge.startsWith("ééé") && huge.endsWith("… [truncated, 100000 bytes of UTF-8]"), huge);
        assertTrue(huge.length() + 2 <= MAX, huge);
        assertEquals("déjà", truncator.truncate(new Utf8Text("déjà".getBytes(StandardCharsets.UTF_8))).tree().asText());
    }

    /** Written by a custom serializer through the sized array entry points. */
    @JsonSerialize(using = SizedArraySerializer.class)
    record Triple(int a, int b, int c) {
    }

    static class SizedArraySerializer extends StdSerializer<Triple> {
        SizedArraySerializer() {
            super(Triple.class);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void serialize(Triple value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartArray(2);
            gen.writeNumber(value.a());
            gen.writeStartArray(value, 2);
            gen.writeNumber(value.b());
            gen.writeNumber(value.c());
            gen.writeEndArray();
            gen.writeEndArray();
        }
    }

    @Test
    void sizedArraysOpenOneArrayEach() {
        PayloadTruncator.Result result = truncator.truncate(new Triple(1, 2, 3));
        assertFalse(result.truncated());
        assertEquals("[1,[2,3]]", result.tree().toString());
    }

    @Test
    void theLimitIsAtLeastEightyCharacters() {
        PayloadTruncator tiny = new PayloadTruncator(40);
        String kept = tiny.truncate("x".repeat(500)).tree().toString();
        assertTrue(kept.length() > 40 && kept.length() <= 80, () -> "length " + kept.length() + ": " + kept);
    }
}
