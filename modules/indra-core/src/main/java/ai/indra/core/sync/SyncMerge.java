package ai.indra.core.sync;

import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Last-write-wins merge used for memory and task synchronisation. */
public final class SyncMerge {

    private SyncMerge() {
    }

    /** Merges two memory arrays by id, keeping the newest version. */
    public static JsonArray mergeMemories(JsonArray local, JsonArray remote) {
        return mergeById(local, remote, "updatedAt");
    }

    public static JsonArray mergeTasks(JsonArray local, JsonArray remote) {
        return mergeById(local, remote, "updatedAt");
    }

    private static JsonArray mergeById(JsonArray local, JsonArray remote, String timestampField) {
        Map<String, JsonObject> merged = new LinkedHashMap<String, JsonObject>();
        if (local != null) {
            for (int i = 0; i < local.size(); i++) {
                JsonObject item = local.getObject(i);
                if (item != null && item.getString("id") != null) {
                    merged.put(item.getString("id"), item);
                }
            }
        }
        if (remote != null) {
            for (int i = 0; i < remote.size(); i++) {
                JsonObject incoming = remote.getObject(i);
                if (incoming == null || incoming.getString("id") == null) {
                    continue;
                }
                String id = incoming.getString("id");
                JsonObject existing = merged.get(id);
                if (existing == null || incoming.getLong(timestampField, 0L) >= existing.getLong(timestampField, 0L)) {
                    merged.put(id, incoming);
                }
            }
        }
        JsonArray out = new JsonArray();
        for (JsonObject item : merged.values()) {
            // A deleted record loses to nothing: keep tombstone so other devices delete too.
            out.add(item);
        }
        return out;
    }

    /** Settings merge: the newer settings document wins field by field. */
    public static JsonObject mergeSettings(JsonObject local, JsonObject remote) {
        if (local == null || local.isEmpty()) {
            return remote == null ? new JsonObject() : remote;
        }
        if (remote == null || remote.isEmpty()) {
            return local;
        }
        long localUpdated = local.getLong("updatedAt", 0L);
        long remoteUpdated = remote.getLong("updatedAt", 0L);
        return remoteUpdated >= localUpdated ? remote : local;
    }
}
