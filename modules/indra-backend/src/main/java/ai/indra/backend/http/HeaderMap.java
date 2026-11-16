package ai.indra.backend.http;

import java.util.LinkedHashMap;
import java.util.Map;

/** Case-insensitive HTTP header holder (no dependency on the JDK-only HTTP server). */
public final class HeaderMap {

    private final Map<String, String> values = new LinkedHashMap<String, String>();

    public void set(String name, String value) {
        if (name == null || value == null) {
            return;
        }
        values.put(name, value);
    }

    public void add(String name, String value) {
        if (name == null || value == null) {
            return;
        }
        String existing = getFirst(name);
        values.put(name, existing == null ? value : existing + ", " + value);
    }

    public String getFirst(String name) {
        if (name == null) {
            return null;
        }
        String direct = values.get(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean has(String name) {
        return getFirst(name) != null;
    }

    public Map<String, String> asMap() {
        return new LinkedHashMap<String, String>(values);
    }

    public int size() {
        return values.size();
    }
}
