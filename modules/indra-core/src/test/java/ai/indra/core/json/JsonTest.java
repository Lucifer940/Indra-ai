package ai.indra.core.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void roundTripsNestedDocument() {
        JsonObject root = new JsonObject();
        root.put("name", "Indra");
        root.put("version", Long.valueOf(3));
        root.put("active", Boolean.TRUE);
        JsonArray tags = new JsonArray();
        tags.add("ai");
        tags.add("assistant");
        root.put("tags", tags);
        JsonObject nested = new JsonObject();
        nested.put("deep", Double.valueOf(1.5d));
        root.put("meta", nested);

        String json = root.toJson();
        JsonObject parsed = JsonObject.parse(json);

        assertEquals("Indra", parsed.getString("name"));
        assertEquals(3L, parsed.getLong("version", 0L));
        assertTrue(parsed.getBoolean("active", false));
        assertEquals(2, parsed.getArray("tags").size());
        assertEquals("assistant", parsed.getArray("tags").getString(1, ""));
        assertEquals(1.5d, parsed.getObject("meta").getDouble("deep", 0d), 0.0001d);
    }

    @Test
    void escapesAndUnescapesSpecialCharacters() {
        JsonObject root = new JsonObject();
        root.put("text", "line1\nline2\t\"quoted\" back\\slash \u0001");
        JsonObject parsed = JsonObject.parse(root.toJson());
        assertEquals("line1\nline2\t\"quoted\" back\\slash \u0001", parsed.getString("text"));
    }

    @Test
    void handlesUnicodeNepali() {
        JsonObject root = new JsonObject();
        root.put("greeting", "नमस्ते, म इन्द्र हुँ।");
        JsonObject parsed = JsonObject.parse(root.toJson());
        assertEquals("नमस्ते, म इन्द्र हुँ।", parsed.getString("greeting"));
    }

    @Test
    void parsesEscapedUnicodeSequence() {
        JsonObject parsed = JsonObject.parse("{\"a\":\"\\u0928\"}");
        assertEquals("न", parsed.getString("a"));
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(JsonException.class, () -> Json.parse("{\"a\":1} junk"));
    }

    @Test
    void rejectsUnterminatedObject() {
        assertThrows(JsonException.class, () -> Json.parse("{\"a\":1"));
    }

    @Test
    void rejectsDeeplyNestedPayload() {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            json.append('[');
        }
        assertThrows(JsonException.class, () -> Json.parse(json.toString()));
    }

    @Test
    void typedGettersFallBackSafely() {
        JsonObject object = JsonObject.parse("{\"n\":\"not-a-number\",\"arr\":[{\"id\":\"x\"}]}");
        assertEquals(42L, object.getLong("n", 42L));
        assertEquals("fallback", object.getString("missing", "fallback"));
        assertNotNull(object.getArray("arr").getObject(0));
        assertEquals("x", object.getArray("arr").getObject(0).getString("id"));
    }

    @Test
    void numbersSerializeWithoutScientificNoise() {
        JsonObject object = new JsonObject();
        object.put("whole", Double.valueOf(5.0d));
        object.put("fraction", Double.valueOf(0.25d));
        String json = object.toJson();
        assertTrue(json.contains("\"whole\":5"));
        assertTrue(json.contains("\"fraction\":0.25"));
        assertFalse(json.contains("E"));
    }

    @Test
    void normalisesMapsAndLists() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<String, Object>();
        map.put("list", java.util.Arrays.asList("a", "b"));
        JsonObject object = new JsonObject(map);
        assertEquals(2, object.getArray("list").size());
        assertTrue(object.toJson().contains("[\"a\",\"b\"]"));
    }
}
