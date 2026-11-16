package ai.indra.core.json;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Small, dependency-free JSON reader/writer used across INDRA AI.
 *
 * <p>Values map to: {@link JsonObject}, {@link JsonArray}, {@link String},
 * {@link Long}, {@link Double}, {@link Boolean} and {@code null}. The reader is
 * a strict recursive-descent parser with a nesting-depth guard so that hostile
 * payloads cannot exhaust the stack.</p>
 */
public final class Json {

    private static final int MAX_DEPTH = 64;

    private Json() {
    }

    // ------------------------------------------------------------------ write

    /** Serialises any supported value to compact JSON. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder(128);
        writeValue(out, normalise(value), 0, false);
        return out.toString();
    }

    /** Serialises any supported value to indented JSON (2 spaces). */
    public static String writePretty(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, normalise(value), 0, true);
        return out.toString();
    }

    /** Converts arbitrary Java containers into JSON-compatible structures. */
    public static Object normalise(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof JsonObject || value instanceof JsonArray) {
            return value;
        }
        if (value instanceof Map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() != null) {
                    object.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return object;
        }
        if (value instanceof Collection) {
            JsonArray array = new JsonArray();
            for (Object item : (Collection<?>) value) {
                array.add(normalise(item));
            }
            return array;
        }
        if (value instanceof Number) {
            if (value instanceof Double || value instanceof Float) {
                double d = ((Number) value).doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    return null;
                }
                return Double.valueOf(d);
            }
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                return Long.valueOf(((Number) value).longValue());
            }
            return Double.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        if (value.getClass().isArray()) {
            JsonArray array = new JsonArray();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.add(normalise(java.lang.reflect.Array.get(value, i)));
            }
            return array;
        }
        return String.valueOf(value);
    }

    private static void writeValue(StringBuilder out, Object value, int depth, boolean pretty) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof JsonObject || value instanceof Map) {
            writeObject(out, (Map<?, ?>) value, depth, pretty);
        } else if (value instanceof JsonArray || value instanceof Collection) {
            writeArray(out, (Collection<?>) value, depth, pretty);
        } else if (value instanceof Number) {
            writeNumber(out, (Number) value);
        } else if (value instanceof Boolean) {
            out.append(((Boolean) value).booleanValue() ? "true" : "false");
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map, int depth, boolean pretty) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, depth + 1, pretty);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            if (pretty) {
                out.append(' ');
            }
            writeValue(out, entry.getValue(), depth + 1, pretty);
        }
        newline(out, depth, pretty);
        out.append('}');
    }

    private static void writeArray(StringBuilder out, Collection<?> values, int depth, boolean pretty) {
        if (values.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, depth + 1, pretty);
            writeValue(out, value, depth + 1, pretty);
        }
        newline(out, depth, pretty);
        out.append(']');
    }

    private static void newline(StringBuilder out, int depth, boolean pretty) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    private static void writeNumber(StringBuilder out, Number number) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                out.append("null");
                return;
            }
            if (d == Math.rint(d) && Math.abs(d) < 1.0e15d) {
                out.append((long) d);
                return;
            }
            out.append(d);
        } else {
            out.append(number.longValue());
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        out.append(String.format("\\u%04x", Integer.valueOf(c)));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    // ------------------------------------------------------------------- read

    /** Parses a JSON document. */
    public static Object parse(String json) {
        if (json == null) {
            throw new JsonException("Cannot parse null JSON");
        }
        Parser parser = new Parser(json);
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("Trailing content at position " + parser.position());
        }
        return value;
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        int position() {
            return index;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    break;
                }
            }
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new JsonException("JSON nesting too deep");
            }
            skipWhitespace();
            if (atEnd()) {
                throw new JsonException("Unexpected end of JSON input");
            }
            char c = text.charAt(index);
            switch (c) {
                case '{':
                    return readObject(depth);
                case '[':
                    return readArray(depth);
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return readNumber();
                    }
                    throw new JsonException("Unexpected character '" + c + "' at position " + index);
            }
        }

        private void expect(String literal) {
            if (!text.startsWith(literal, index)) {
                throw new JsonException("Expected " + literal + " at position " + index);
            }
            index += literal.length();
        }

        private JsonObject readObject(int depth) {
            JsonObject object = new JsonObject();
            index++; // consume '{'
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == '}') {
                index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(index) != '"') {
                    throw new JsonException("Expected object key at position " + index);
                }
                String key = readString();
                skipWhitespace();
                if (atEnd() || text.charAt(index) != ':') {
                    throw new JsonException("Expected ':' at position " + index);
                }
                index++;
                Object value = readValue(depth + 1);
                object.put(key, value);
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonException("Unterminated object");
                }
                char c = text.charAt(index);
                if (c == ',') {
                    index++;
                } else if (c == '}') {
                    index++;
                    return object;
                } else {
                    throw new JsonException("Expected ',' or '}' at position " + index);
                }
            }
        }

        private JsonArray readArray(int depth) {
            JsonArray array = new JsonArray();
            index++; // consume '['
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == ']') {
                index++;
                return array;
            }
            while (true) {
                array.add(readValue(depth + 1));
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonException("Unterminated array");
                }
                char c = text.charAt(index);
                if (c == ',') {
                    index++;
                } else if (c == ']') {
                    index++;
                    return array;
                } else {
                    throw new JsonException("Expected ',' or ']' at position " + index);
                }
            }
        }

        private String readString() {
            index++; // consume opening quote
            StringBuilder out = new StringBuilder(32);
            while (true) {
                if (atEnd()) {
                    throw new JsonException("Unterminated string");
                }
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw new JsonException("Unterminated escape sequence");
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '/':
                        out.append('/');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (index + 4 > text.length()) {
                            throw new JsonException("Truncated unicode escape");
                        }
                        String hex = text.substring(index, index + 4);
                        index += 4;
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException error) {
                            throw new JsonException("Invalid unicode escape \\u" + hex);
                        }
                        break;
                    default:
                        throw new JsonException("Invalid escape \\" + escape);
                }
            }
        }

        private Number readNumber() {
            int start = index;
            if (!atEnd() && text.charAt(index) == '-') {
                index++;
            }
            boolean fractional = false;
            while (!atEnd()) {
                char c = text.charAt(index);
                if (c >= '0' && c <= '9') {
                    index++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    fractional = fractional || c == '.' || c == 'e' || c == 'E';
                    index++;
                } else {
                    break;
                }
            }
            String raw = text.substring(start, index);
            try {
                if (fractional) {
                    return Double.valueOf(Double.parseDouble(raw));
                }
                return Long.valueOf(Long.parseLong(raw));
            } catch (NumberFormatException error) {
                throw new JsonException("Invalid number '" + raw + "'");
            }
        }
    }
}
