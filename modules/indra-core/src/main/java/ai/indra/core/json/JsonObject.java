package ai.indra.core.json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON object with typed, null-safe accessors. Backed by a
 * {@link LinkedHashMap} so field order is preserved when serialised.
 */
public class JsonObject extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    public JsonObject() {
        super();
    }

    public JsonObject(Map<String, ?> source) {
        super();
        if (source != null) {
            for (Map.Entry<String, ?> entry : source.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** Builds an object from alternating key/value arguments. */
    public static JsonObject of(Object... keyValues) {
        JsonObject object = new JsonObject();
        if (keyValues != null) {
            for (int i = 0; i + 1 < keyValues.length; i += 2) {
                object.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return object;
    }

    /** Parses a JSON document and requires the root to be an object. */
    public static JsonObject parse(String json) {
        Object parsed = Json.parse(json);
        if (parsed instanceof JsonObject) {
            return (JsonObject) parsed;
        }
        throw new JsonException("Expected a JSON object at the document root");
    }

    /** Parses a JSON document, returning an empty object for blank input. */
    public static JsonObject parseOrEmpty(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new JsonObject();
        }
        return parse(json);
    }

    @Override
    public JsonObject put(String key, Object value) {
        super.put(key, Json.normalise(value));
        return this;
    }

    public JsonObject removeKey(String key) {
        super.remove(key);
        return this;
    }

    public JsonObject merge(JsonObject other) {
        if (other != null) {
            for (Map.Entry<String, Object> entry : other.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    public boolean has(String key) {
        return get(key) != null;
    }

    public String getString(String key, String fallback) {
        Object value = get(key);
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public long getLong(String key, long fallback) {
        Object value = get(key);
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

    public int getInt(String key, int fallback) {
        return (int) getLong(key, fallback);
    }

    public double getDouble(String key, double fallback) {
        Object value = get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = get(key);
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        return fallback;
    }

    public JsonObject getObject(String key) {
        Object value = get(key);
        if (value instanceof JsonObject) {
            return (JsonObject) value;
        }
        if (value instanceof Map) {
            return new JsonObject((Map<String, ?>) value);
        }
        return null;
    }

    public JsonObject getObjectOrEmpty(String key) {
        JsonObject object = getObject(key);
        return object == null ? new JsonObject() : object;
    }

    public JsonArray getArray(String key) {
        Object value = get(key);
        if (value instanceof JsonArray) {
            return (JsonArray) value;
        }
        if (value instanceof List) {
            return new JsonArray((List<?>) value);
        }
        return null;
    }

    public JsonArray getArrayOrEmpty(String key) {
        JsonArray array = getArray(key);
        return array == null ? new JsonArray() : array;
    }

    public String toJson() {
        return Json.write(this);
    }

    public String toPrettyJson() {
        return Json.writePretty(this);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
