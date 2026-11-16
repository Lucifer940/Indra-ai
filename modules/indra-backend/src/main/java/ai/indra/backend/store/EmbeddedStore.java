package ai.indra.backend.store;

import ai.indra.core.json.Json;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File-backed document store used by default (no database server needed).
 *
 * <p>Each collection lives in its own JSON file and every mutation is written
 * atomically (temp file + move), so a crash cannot leave a half-written file.
 * Suitable for a personal deployment; switch to PostgreSQL for multi-user
 * production use via {@code INDRA_DB_URL}.</p>
 */
public final class EmbeddedStore implements DocumentStore {

    private final File directory;
    private final FileCodec codec;
    private final Map<String, Map<String, Entry>> collections = new LinkedHashMap<String, Map<String, Entry>>();
    private final Object lock = new Object();

    /** One stored document plus its index fields. */
    private static final class Entry {
        String id;
        String userId;
        String lookupKey;
        long updatedAt;
        JsonObject document;

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.put("id", id);
            json.put("userId", userId);
            json.put("lookupKey", lookupKey);
            json.put("updatedAt", Long.valueOf(updatedAt));
            json.put("document", document);
            return json;
        }

        static Entry fromJson(JsonObject json) {
            Entry entry = new Entry();
            entry.id = json.getString("id");
            entry.userId = json.getString("userId");
            entry.lookupKey = json.getString("lookupKey");
            entry.updatedAt = json.getLong("updatedAt", 0L);
            entry.document = json.getObjectOrEmpty("document");
            return entry;
        }
    }

    public EmbeddedStore(File directory) {
        this(directory, FileCodec.PLAIN);
    }

    public EmbeddedStore(File directory, FileCodec codec) {
        this.directory = directory;
        this.codec = codec == null ? FileCodec.PLAIN : codec;
    }

    @Override
    public void init() {
        synchronized (lock) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Cannot create store directory " + directory.getAbsolutePath());
            }
            File[] files = directory.listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (!file.getName().endsWith(".json")) {
                    continue;
                }
                String collection = file.getName().substring(0, file.getName().length() - 5);
                try {
                    String text = new String(codec.decode(Files.readAllBytes(file.toPath())), StandardCharsets.UTF_8);
                    JsonObject root = JsonObject.parseOrEmpty(text);
                    Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
                    for (String id : root.keySet()) {
                        JsonObject entryJson = root.getObject(id);
                        if (entryJson != null) {
                            entries.put(id, Entry.fromJson(entryJson));
                        }
                    }
                    collections.put(collection, entries);
                } catch (IOException | RuntimeException error) {
                    Log.error("EmbeddedStore", "Could not load collection " + collection, error);
                }
            }
            Log.info("EmbeddedStore", "Loaded " + collections.size() + " collections from " + directory.getAbsolutePath());
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            for (String collection : collections.keySet()) {
                flush(collection);
            }
        }
    }

    private Map<String, Entry> bucket(String collection) {
        Map<String, Entry> entries = collections.get(collection);
        if (entries == null) {
            entries = new LinkedHashMap<String, Entry>();
            collections.put(collection, entries);
        }
        return entries;
    }

    @Override
    public JsonObject get(String collection, String id) {
        synchronized (lock) {
            Entry entry = bucket(collection).get(id);
            return entry == null ? null : entry.document;
        }
    }

    @Override
    public void put(String collection, String id, String userId, String lookupKey, long updatedAt, JsonObject document) {
        synchronized (lock) {
            Entry entry = new Entry();
            entry.id = id;
            entry.userId = userId;
            entry.lookupKey = lookupKey;
            entry.updatedAt = updatedAt;
            entry.document = document;
            bucket(collection).put(id, entry);
            flush(collection);
        }
    }

    @Override
    public void delete(String collection, String id) {
        synchronized (lock) {
            Map<String, Entry> entries = bucket(collection);
            if (entries.remove(id) != null) {
                flush(collection);
            }
        }
    }

    @Override
    public List<JsonObject> list(String collection, String userId, long updatedAfter, int limit) {
        synchronized (lock) {
            List<JsonObject> result = new ArrayList<JsonObject>();
            for (Entry entry : bucket(collection).values()) {
                if (userId != null && !userId.equals(entry.userId)) {
                    continue;
                }
                if (updatedAfter > 0L && entry.updatedAt <= updatedAfter) {
                    continue;
                }
                result.add(entry.document);
            }
            Collections.sort(result, new java.util.Comparator<JsonObject>() {
                @Override
                public int compare(JsonObject left, JsonObject right) {
                    return Long.compare(left.getLong("updatedAt", 0L), right.getLong("updatedAt", 0L));
                }
            });
            if (limit > 0 && result.size() > limit) {
                return new ArrayList<JsonObject>(result.subList(0, limit));
            }
            return result;
        }
    }

    @Override
    public JsonObject findByLookup(String collection, String lookupKey) {
        if (lookupKey == null) {
            return null;
        }
        synchronized (lock) {
            for (Entry entry : bucket(collection).values()) {
                if (lookupKey.equals(entry.lookupKey)) {
                    return entry.document;
                }
            }
            return null;
        }
    }

    @Override
    public int count(String collection) {
        synchronized (lock) {
            return bucket(collection).size();
        }
    }

    @Override
    public void deleteUserData(String userId) {
        synchronized (lock) {
            for (Map.Entry<String, Map<String, Entry>> bucket : collections.entrySet()) {
                Iterator<Map.Entry<String, Entry>> iterator = bucket.getValue().entrySet().iterator();
                boolean changed = false;
                while (iterator.hasNext()) {
                    if (userId.equals(iterator.next().getValue().userId)) {
                        iterator.remove();
                        changed = true;
                    }
                }
                if (changed) {
                    flush(bucket.getKey());
                }
            }
        }
    }

    @Override
    public String describe() {
        return "json-file:" + directory.getAbsolutePath();
    }

    /** Writes a collection to disk atomically. */
    private void flush(String collection) {
        Map<String, Entry> entries = collections.get(collection);
        if (entries == null) {
            return;
        }
        JsonObject root = new JsonObject();
        for (Entry entry : entries.values()) {
            root.put(entry.id, entry.toJson());
        }
        String payload = Json.writePretty(root);
        File target = new File(directory, collection + ".json");
        File temp = new File(directory, collection + ".json.tmp");
        try {
            Files.write(temp.toPath(), codec.encode(payload.getBytes(StandardCharsets.UTF_8)));
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException fallback) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (UnsupportedOperationException fallback) {
                // Some Android file systems refuse ATOMIC_MOVE; a plain replace is fine.
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot persist collection " + collection, error);
        }
    }
}
