package ai.indra.core.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A JSON array. Values are plain Java objects: {@link JsonObject},
 * {@link JsonArray}, {@link String}, {@link Long}, {@link Double},
 * {@link Boolean} or {@code null}.
 */
public class JsonArray extends ArrayList<Object> {

    private static final long serialVersionUID = 1L;

    public JsonArray() {
        super();
    }

    public JsonArray(int initialCapacity) {
        super(initialCapacity);
    }

    public JsonArray(Collection<?> values) {
        super(values);
    }

    public static JsonArray of(Object... values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (Object value : values) {
                array.add(value);
            }
        }
        return array;
    }

    /** Fluent add so arrays can be built inline. */
    public JsonArray append(Object value) {
        add(value);
        return this;
    }

    public JsonObject getObject(int index) {
        Object value = index >= 0 && index < size() ? get(index) : null;
        if (value instanceof JsonObject) {
            return (JsonObject) value;
        }
        if (value instanceof Map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return object;
        }
        return null;
    }

    public JsonArray getArray(int index) {
        Object value = index >= 0 && index < size() ? get(index) : null;
        if (value instanceof JsonArray) {
            return (JsonArray) value;
        }
        if (value instanceof List) {
            return new JsonArray((List<?>) value);
        }
        return null;
    }

    public String getString(int index, String fallback) {
        Object value = index >= 0 && index < size() ? get(index) : null;
        return value == null ? fallback : String.valueOf(value);
    }

    public long getLong(int index, long fallback) {
        Object value = index >= 0 && index < size() ? get(index) : null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public List<String> asStringList() {
        List<String> result = new ArrayList<String>(size());
        for (Object value : this) {
            if (value != null) {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }

    public String toJson() {
        return Json.write(this);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
