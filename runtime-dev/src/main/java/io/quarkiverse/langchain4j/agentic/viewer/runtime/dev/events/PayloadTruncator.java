package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.events;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.util.TokenBuffer;

/**
 * Turns event payload values into bounded, plain JSON values (maps, lists, strings, numbers, booleans, null): large
 * scope values or outputs would slow the Dev UI and use memory. Plain values, not Jackson trees: the Dev UI serializer
 * writes a JsonNode as a bean.
 * <p>
 * A value is cut while Jackson serializes it, within the limit of JSON characters: strings are cut with a marker, and
 * arrays and objects keep their first entries followed by a marker counting the others. Neither the full tree nor the
 * full JSON text of a large value is built, and the serialization stops once it has skipped a bounded amount of it (the
 * markers then tell that more was left out, without a count). Streaming outputs and values Jackson cannot convert
 * (failing getters, cycles, a map or list containing itself) are replaced by a marker, never by their {@code toString()}
 * (a streaming response would block). It never throws: an event must not be lost for its payload.
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
    /** Tokens skipped beyond the limit before the serialization of a value stops. */
    private static final int SKIPPED_TOKENS = 10_000;
    /** Deeper values are taken for cycles (Jackson recurses without end into a map or list containing itself). */
    private static final int MAX_DEPTH = 1000;

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
        if (value == null) {
            return new Result(NODES.nullNode(), false);
        }
        if (isStream(value.getClass())) {
            return new Result(TextNode.valueOf("<stream " + value.getClass().getName() + ">"), false);
        }
        TokenBuffer buffer = new TokenBuffer(MAPPER, false);
        Bounded bounded = new Bounded(buffer, maxSize);
        try {
            MAPPER.writeValue(bounded, value);
        } catch (Exception | StackOverflowError e) {
            if (!bounded.stopped) {
                return notSerializable(value);
            }
        }
        try {
            if (bounded.stopped) {
                bounded.endOpenContainers();
            }
            return new Result(MAPPER.readTree(buffer.asParser()), bounded.truncated);
        } catch (IOException | RuntimeException e) {
            return notSerializable(value);
        }
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

    private static Result notSerializable(Object value) {
        return new Result(TextNode.valueOf("<" + value.getClass().getName() + ", not serializable to JSON>"), false);
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

    /**
     * Writes what Jackson serializes into a token buffer, within a budget of JSON characters: what does not fit is
     * skipped and counted per container, whose end then writes a marker. Room for the marker of each open container is
     * kept. Every write method goes through it (none is passed straight to the buffer).
     */
    private static final class Bounded extends JsonGeneratorDelegate {

        /** A container being written. */
        private static final class Open {
            final boolean array;
            int entries;
            int skipped;

            Open(boolean array) {
                this.array = array;
            }
        }

        private final int budget;
        private final Deque<Open> open = new ArrayDeque<>();
        /** JSON characters written so far, with the brackets of the open containers */
        private int used;
        /** depth within a skipped container */
        private int skipping;
        /** the field name was skipped, so is its value */
        private boolean skipValue;
        private int skippedTokens;
        boolean truncated;
        /** the serialization stopped: too much was skipped */
        boolean stopped;

        Bounded(TokenBuffer buffer, int budget) {
            super(buffer, false);
            this.budget = budget;
        }

        /** Room left for a value, keeping the room of the markers of the open containers. */
        private int room(int comma) {
            return budget - used - comma - open.size() * MARKER_ROOM;
        }

        private int comma() {
            Open parent = open.peek();
            return parent != null && parent.array && parent.entries > 0 ? 1 : 0;
        }

        /** Skips a token; past the bound, the serialization stops. */
        private void skip() {
            if (++skippedTokens > SKIPPED_TOKENS) {
                stopped = true;
                throw new IllegalStateException("payload too large, the rest is left out");
            }
        }

        /** Whether the next value is skipped (inside a skipped container, after a skipped field name). */
        private boolean skipped(boolean container) {
            if (skipping > 0 || skipValue) {
                skipValue = false;
                if (container) {
                    skipping++;
                }
                skip();
                return true;
            }
            return false;
        }

        /**
         * Whether a container is written: always at the root and in an object (its entries are then bounded, the room of
         * its marker is kept by the field), in an array when it fits with the room of its marker (otherwise skipped and
         * counted).
         */
        private boolean opens() {
            if (skipped(true)) {
                return false;
            }
            Open parent = open.peek();
            int comma = comma();
            if (parent != null && parent.array && (parent.skipped > 0 || 2 + MARKER_ROOM > room(comma))) {
                skipEntry(parent, true);
                return false;
            }
            used += comma + 2;
            if (parent != null && parent.array) {
                parent.entries++;
            }
            return true;
        }

        private void skipEntry(Open parent, boolean container) {
            parent.skipped++;
            truncated = true;
            if (container) {
                skipping++;
            }
            skip();
        }

        /**
         * A scalar value of this JSON length, written when it fits the room left (at the root, in an object or in an
         * array); otherwise {@code asText} is written instead as a string, cut (in an array: skipped and counted when
         * not even a cut one fits), so a field name always gets a value.
         */
        private void scalar(int size, Write write, String asText) throws IOException {
            if (skipped(false)) {
                return;
            }
            Open parent = open.peek();
            int comma = comma();
            if (size <= room(comma) && (parent == null || !parent.array || parent.skipped == 0)) {
                used += comma + size;
                if (parent != null && parent.array) {
                    parent.entries++;
                }
                write.run();
            } else {
                writeMarker(asText);
            }
        }

        private void scalar(String text, Write write) throws IOException {
            scalar(text.length(), write, text);
        }

        private interface Write {
            void run() throws IOException;
        }

        private void start(boolean array, Write write) throws IOException {
            if (open.size() + skipping >= MAX_DEPTH) {
                throw new IllegalStateException("nested deeper than " + MAX_DEPTH + " levels");
            }
            if (opens()) {
                write.run();
                open.push(new Open(array));
            }
        }

        private void end() throws IOException {
            if (skipping > 0) {
                skipping--;
                skip();
                return;
            }
            Open container = open.pop();
            close(container, container.skipped > 0 ? container.skipped + " " : null);
        }

        /**
         * Ends a container, with the marker of the entries left out when {@code count} is given ("" for unknown): it
         * takes the room kept for it.
         */
        private void close(Open container, String count) throws IOException {
            int comma = container.entries > 0 ? 1 : 0;
            if (container.array) {
                if (count != null) {
                    String marker = "… [" + count + "more items]";
                    used += comma + marker.length() + 2;
                    delegate.writeString(marker);
                }
                delegate.writeEndArray();
            } else {
                if (count != null) {
                    String marker = "[" + count + "more fields]";
                    used += comma + 4 + marker.length() + 2;
                    delegate.writeFieldName("…");
                    delegate.writeString(marker);
                }
                delegate.writeEndObject();
            }
        }

        /** After a stop: the open containers end with a marker telling that more was left out. */
        void endOpenContainers() throws IOException {
            truncated = true;
            while (!open.isEmpty()) {
                close(open.pop(), "");
            }
        }

        @Override
        public void writeFieldName(String name) throws IOException {
            if (skipping > 0) {
                skip();
                return;
            }
            Open parent = open.peek();
            int comma = parent.entries > 0 ? 1 : 0;
            int size = name.length() + 3;
            if (parent.skipped > 0 || size + MARKER_ROOM > room(comma)) {
                parent.skipped++;
                truncated = true;
                skipValue = true;
                skip();
                return;
            }
            used += comma + size;
            parent.entries++;
            delegate.writeFieldName(name);
        }

        @Override
        public void writeString(String text) throws IOException {
            if (text == null) {
                writeNull();
            } else {
                writeText(text, text.length());
            }
        }

        /**
         * A string is cut to the room left; in an array, it is skipped when not even a cut one fits.
         *
         * @param text the string, or its start when longer than the budget
         * @param length the length of the whole string
         */
        private void writeText(String text, int length) throws IOException {
            writeText(text, length, length + " chars");
        }

        /** @param size the size told by the marker of a cut string */
        private void writeText(String text, int length, String size) throws IOException {
            if (skipped(false)) {
                return;
            }
            Open parent = open.peek();
            boolean inArray = parent != null && parent.array;
            int comma = comma();
            int room = room(comma);
            if (inArray && (parent.skipped > 0 || Math.min(length + 2, MARKER_ROOM) > room)) {
                skipEntry(parent, false);
                return;
            }
            String kept = length + 2 <= room ? text : cut(text, size, Math.max(room, MARKER_ROOM));
            truncated |= kept != text;
            used += comma + kept.length() + 2;
            if (inArray) {
                parent.entries++;
            }
            delegate.writeString(kept);
        }

        /** A marker string standing for a value left out. */
        private void writeMarker(String marker) throws IOException {
            truncated = true;
            writeText(marker, marker.length());
        }

        // every other write goes through the ones above

        @Override
        public void writeStartArray() throws IOException {
            start(true, () -> delegate.writeStartArray());
        }

        @Override
        @Deprecated
        public void writeStartArray(int size) throws IOException {
            start(true, () -> delegate.writeStartArray());
        }

        @Override
        public void writeStartArray(Object forValue) throws IOException {
            start(true, () -> delegate.writeStartArray(forValue));
        }

        @Override
        public void writeStartArray(Object forValue, int size) throws IOException {
            start(true, () -> delegate.writeStartArray(forValue, size));
        }

        @Override
        public void writeEndArray() throws IOException {
            end();
        }

        @Override
        public void writeStartObject() throws IOException {
            start(false, () -> delegate.writeStartObject());
        }

        @Override
        public void writeStartObject(Object forValue) throws IOException {
            start(false, () -> delegate.writeStartObject(forValue));
        }

        @Override
        public void writeStartObject(Object forValue, int size) throws IOException {
            start(false, () -> delegate.writeStartObject(forValue, size));
        }

        @Override
        public void writeEndObject() throws IOException {
            end();
        }

        @Override
        public void writeFieldName(SerializableString name) throws IOException {
            writeFieldName(name.getValue());
        }

        @Override
        public void writeFieldId(long id) throws IOException {
            writeFieldName(Long.toString(id));
        }

        @Override
        public void writeArray(int[] array, int offset, int length) throws IOException {
            writeStartArray(array, length);
            for (int i = offset; i < offset + length; i++) {
                writeNumber(array[i]);
            }
            writeEndArray();
        }

        @Override
        public void writeArray(long[] array, int offset, int length) throws IOException {
            writeStartArray(array, length);
            for (int i = offset; i < offset + length; i++) {
                writeNumber(array[i]);
            }
            writeEndArray();
        }

        @Override
        public void writeArray(double[] array, int offset, int length) throws IOException {
            writeStartArray(array, length);
            for (int i = offset; i < offset + length; i++) {
                writeNumber(array[i]);
            }
            writeEndArray();
        }

        @Override
        public void writeArray(String[] array, int offset, int length) throws IOException {
            writeStartArray(array, length);
            for (int i = offset; i < offset + length; i++) {
                writeString(array[i]);
            }
            writeEndArray();
        }

        @Override
        public void writeString(char[] text, int offset, int length) throws IOException {
            // a long text is only copied up to what can be kept
            writeText(new String(text, offset, Math.min(length, budget)), length);
        }

        @Override
        public void writeString(SerializableString text) throws IOException {
            writeString(text.getValue());
        }

        @Override
        public void writeString(Reader reader, int length) throws IOException {
            writeString("<text stream>");
        }

        @Override
        public void writeRawUTF8String(byte[] text, int offset, int length) throws IOException {
            writeUtf8(text, offset, length);
        }

        @Override
        public void writeUTF8String(byte[] text, int offset, int length) throws IOException {
            writeUtf8(text, offset, length);
        }

        /** Decodes only what can be kept (4 bytes at most per char); the marker tells the length in UTF-16 chars. */
        private void writeUtf8(byte[] text, int offset, int length) throws IOException {
            if (skipped(false)) {
                return;
            }
            if (length > 4 * budget) { // more chars than the budget whatever they are: not counted, its bytes are told
                writeText(new String(text, offset, 4 * budget, StandardCharsets.UTF_8), budget + 1,
                        length + " bytes of UTF-8");
                return;
            }
            int chars = 0;
            for (int i = offset; i < offset + length; i++) {
                byte b = text[i];
                if ((b & 0xC0) != 0x80) { // not a continuation byte: a char starts, two for a 4-byte one
                    chars += (b & 0xF8) == 0xF0 ? 2 : 1;
                }
            }
            writeText(new String(text, offset, length, StandardCharsets.UTF_8), chars);
        }

        @Override
        public void writeRawValue(String text) throws IOException {
            writeString(text); // raw JSON is kept as text: it cannot be cut as JSON
        }

        @Override
        public void writeRawValue(String text, int offset, int length) throws IOException {
            writeText(text.substring(offset, offset + Math.min(length, budget)), length);
        }

        @Override
        public void writeRawValue(char[] text, int offset, int length) throws IOException {
            writeString(text, offset, length);
        }

        @Override
        public void writeBinary(Base64Variant variant, byte[] data, int offset, int length) throws IOException {
            scalar((length + 2) / 3 * 4 + 2, () -> delegate.writeBinary(variant, data, offset, length),
                    "<" + length + " bytes>");
        }

        @Override
        public int writeBinary(Base64Variant variant, InputStream data, int length) throws IOException {
            writeString("<binary stream>");
            return 0;
        }

        @Override
        public void writeNumber(short v) throws IOException {
            scalar(Short.toString(v), () -> delegate.writeNumber(v));
        }

        @Override
        public void writeNumber(int v) throws IOException {
            scalar(Integer.toString(v), () -> delegate.writeNumber(v));
        }

        @Override
        public void writeNumber(long v) throws IOException {
            scalar(Long.toString(v), () -> delegate.writeNumber(v));
        }

        @Override
        public void writeNumber(BigInteger v) throws IOException {
            if (v == null) {
                writeNull();
            } else if (digits(v.bitLength()) > budget) { // never turned into its whole decimal text
                writeMarker(hugeNumber(digits(v.bitLength())));
            } else {
                scalar(v.toString(), () -> delegate.writeNumber(v));
            }
        }

        @Override
        public void writeNumber(double v) throws IOException {
            scalar(Double.toString(v), () -> delegate.writeNumber(v));
        }

        @Override
        public void writeNumber(float v) throws IOException {
            scalar(Float.toString(v), () -> delegate.writeNumber(v));
        }

        @Override
        public void writeNumber(BigDecimal v) throws IOException {
            // its text is its digits and a short exponent (plain digits for a small scale)
            int digits = v == null ? 0 : digits(v.unscaledValue().bitLength()) + Math.min(Math.abs(v.scale()), 12) + 2;
            if (v == null) {
                writeNull();
            } else if (digits > budget) {
                writeMarker(hugeNumber(digits));
            } else {
                scalar(v.toString(), () -> delegate.writeNumber(v));
            }
        }

        @Override
        public void writeNumber(String encoded) throws IOException {
            if (encoded == null) {
                writeNull();
            } else {
                scalar(encoded, () -> delegate.writeNumber(encoded));
            }
        }

        @Override
        public void writeNumber(char[] encoded, int offset, int length) throws IOException {
            if (length > budget) {
                writeText(new String(encoded, offset, budget), length);
            } else {
                writeNumber(new String(encoded, offset, length));
            }
        }

        @Override
        public void writeBoolean(boolean v) throws IOException {
            scalar(v ? "true" : "false", () -> delegate.writeBoolean(v));
        }

        @Override
        public void writeNull() throws IOException {
            scalar("null", () -> delegate.writeNull());
        }

        @Override
        public void writeEmbeddedObject(Object object) throws IOException {
            writeString("<" + (object == null ? "null" : object.getClass().getName()) + ">");
        }
    }

    /** At least the decimal digits of an integer of this bit length. */
    private static int digits(int bitLength) {
        return (int) (bitLength * 0.30103) + 1;
    }

    private static String hugeNumber(int digits) {
        return "<number of about " + digits + " digits>";
    }

    private static String cut(String text, int budget) {
        return cut(text, text.length() + " chars", budget);
    }

    /** The start of a text, with a marker telling its size, within the budget ({@code text} may be its start only). */
    private static String cut(String text, String size, int budget) {
        String marker = "… [truncated, " + size + "]";
        int keep = Math.min(Math.max(0, budget - marker.length() - 2), text.length());
        if (keep > 0 && Character.isHighSurrogate(text.charAt(keep - 1))) {
            keep--; // never half of a character (an emoji is two UTF-16 chars)
        }
        return text.substring(0, keep) + marker;
    }
}
