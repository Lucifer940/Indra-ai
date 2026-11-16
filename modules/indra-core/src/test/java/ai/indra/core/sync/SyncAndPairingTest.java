package ai.indra.core.sync;

import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncAndPairingTest {

    @Test
    void pairingCodesAreHashedAndVerifiable() {
        String code = PairingCodes.generate();
        String salt = "0123456789abcdef0123";
        String hash = PairingCodes.hash(code, salt);
        assertTrue(PairingCodes.matches(code.toLowerCase(), salt, hash));
        assertFalse(PairingCodes.matches("WRONG-CODE", salt, hash));
        assertEquals(9, code.length());
    }

    @Test
    void mergeKeepsNewerMemoryVersion() {
        JsonObject old = new JsonObject().put("id", "m1").put("content", "old").put("updatedAt", Long.valueOf(1000));
        JsonObject fresh = new JsonObject().put("id", "m1").put("content", "new").put("updatedAt", Long.valueOf(2000));
        JsonArray local = new JsonArray().append(old);
        JsonArray remote = new JsonArray().append(fresh);

        JsonArray merged = SyncMerge.mergeMemories(local, remote);
        assertEquals(1, merged.size());
        assertEquals("new", merged.getObject(0).getString("content"));
    }

    @Test
    void mergeUnionsDistinctRecordsAndKeepsTombstones() {
        JsonArray local = new JsonArray()
                .append(new JsonObject().put("id", "a").put("updatedAt", Long.valueOf(10)))
                .append(new JsonObject().put("id", "b").put("updatedAt", Long.valueOf(10)).put("deletedAt", Long.valueOf(20)));
        JsonArray remote = new JsonArray()
                .append(new JsonObject().put("id", "c").put("updatedAt", Long.valueOf(30)));
        JsonArray merged = SyncMerge.mergeMemories(local, remote);
        assertEquals(3, merged.size());
    }

    @Test
    void settingsMergePicksNewerDocument() {
        JsonObject local = new JsonObject().put("personaId", "friendly").put("updatedAt", Long.valueOf(100));
        JsonObject remote = new JsonObject().put("personaId", "professional").put("updatedAt", Long.valueOf(200));
        assertEquals("professional", SyncMerge.mergeSettings(local, remote).getString("personaId"));
        assertEquals("friendly", SyncMerge.mergeSettings(remote, local).getString("personaId"));
    }

    @Test
    void envelopeRoundTrips() {
        SyncEnvelope envelope = SyncEnvelope.empty("user-1", "device-1")
                .setCursor(42L)
                .setMemories(new JsonArray().append(new JsonObject().put("id", "m1")))
                .setSettings(new JsonObject().put("theme", "dark"))
                .setPlatform("ANDROID");
        SyncEnvelope parsed = SyncEnvelope.fromJson(JsonObject.parse(envelope.toJson()));
        assertEquals("user-1", parsed.getUserId());
        assertEquals(42L, parsed.getCursor());
        assertEquals(1, parsed.getMemories().size());
        assertEquals("dark", parsed.getSettings().getString("theme"));
    }
}
