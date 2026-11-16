package ai.indra.backend;

import ai.indra.backend.store.DocumentStore;
import ai.indra.backend.store.Repository;
import ai.indra.core.json.Json;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Export and import of every stored document (backup / restore). */
public final class BackupService {

    private static final List<String> COLLECTIONS = Arrays.asList(
            Repository.USERS, Repository.MEMORIES, Repository.CONVERSATIONS, Repository.MESSAGES,
            Repository.TASKS, Repository.SETTINGS, Repository.DEVICES, Repository.LICENSES,
            Repository.CONFIG, Repository.API_KEYS, Repository.API_SECRETS, Repository.AUDIT, Repository.META);

    private final DocumentStore store;

    public BackupService(DocumentStore store) {
        this.store = store;
    }

    public JsonObject export(String path) {
        JsonObject backup = new JsonObject();
        backup.put("format", "indra-ai-backup");
        backup.put("version", "1.1.0");
        backup.put("createdAt", Long.valueOf(System.currentTimeMillis()));
        JsonObject collections = new JsonObject();
        int total = 0;
        for (String collection : COLLECTIONS) {
            JsonArray items = new JsonArray();
            for (JsonObject document : store.list(collection, null, 0L, 0)) {
                items.add(document);
                total++;
            }
            collections.put(collection, items);
        }
        backup.put("collections", collections);
        backup.put("documentCount", Integer.valueOf(total));
        File file = new File(path);
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                throw new IOException("Cannot create backup directory");
            }
            Files.write(file.toPath(), Json.writePretty(backup).getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot write backup: " + error.getMessage(), error);
        }
        Log.info("BackupService", "Backup written to " + file.getAbsolutePath() + " (" + total + " documents)");
        return new JsonObject().put("ok", Boolean.TRUE)
                .put("path", file.getAbsolutePath())
                .put("documents", Integer.valueOf(total));
    }

    public JsonObject restore(String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("Backup file not found: " + path);
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonObject backup = JsonObject.parse(text);
            if (!"indra-ai-backup".equals(backup.getString("format", ""))) {
                throw new IllegalArgumentException("Not an INDRA AI backup file");
            }
            JsonObject collections = backup.getObjectOrEmpty("collections");
            int restored = 0;
            for (String collection : collections.keySet()) {
                JsonArray items = collections.getArrayOrEmpty(collection);
                for (int i = 0; i < items.size(); i++) {
                    JsonObject document = items.getObject(i);
                    if (document == null) {
                        continue;
                    }
                    String id = documentId(collection, document);
                    if (id == null) {
                        continue;
                    }
                    String userId = document.getString("userId");
                    if (userId == null && Repository.USERS.equals(collection)) {
                        userId = id;
                    }
                    String lookupKey = Repository.USERS.equals(collection) && document.getString("email") != null
                            ? "email:" + document.getString("email").toLowerCase(java.util.Locale.ROOT) : null;
                    long updatedAt = document.getLong("updatedAt", document.getLong("createdAt", System.currentTimeMillis()));
                    store.put(collection, id, userId, lookupKey, updatedAt, document);
                    restored++;
                }
            }
            Log.info("BackupService", "Restored " + restored + " documents from " + path);
            return new JsonObject().put("ok", Boolean.TRUE).put("documents", Integer.valueOf(restored));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read backup: " + error.getMessage(), error);
        }
    }

    private String documentId(String collection, JsonObject document) {
        if (Repository.CONFIG.equals(collection)) {
            return "app";
        }
        String id = document.getString("id");
        if (id == null && Repository.LICENSES.equals(collection)) {
            id = document.getString("key");
        }
        if (id == null && Repository.META.equals(collection)) {
            id = document.getString("key");
        }
        return id;
    }
}
