package ai.indra.backend.service;

import ai.indra.backend.store.Repository;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.memory.MemoryEngine;
import ai.indra.core.model.MemoryCategory;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.model.MemorySource;
import ai.indra.core.model.TaskItem;
import ai.indra.core.model.User;
import ai.indra.core.model.UserSettings;
import ai.indra.core.security.Validation;
import ai.indra.core.sync.SyncEnvelope;
import ai.indra.core.sync.SyncMerge;
import ai.indra.core.util.Strings;

/**
 * Two-way synchronisation of memories, tasks and settings between the phone, the
 * PC and the server. Last-write-wins per record, with deletion tombstones so a
 * delete on one device propagates to the others.
 */
public final class SyncService {

    private final Repository repository;

    public SyncService(Repository repository) {
        this.repository = repository;
    }

    public JsonObject sync(User user, JsonObject body) {
        SyncEnvelope incoming = SyncEnvelope.fromJson(body);

        // 1. Apply incoming memories (ownership always forced to the caller).
        for (int i = 0; i < incoming.getMemories().size(); i++) {
            JsonObject doc = incoming.getMemories().getObject(i);
            if (doc == null) {
                continue;
            }
            MemoryItem item = MemoryItem.fromJson(doc);
            item.setUserId(user.getId());
            if (Strings.isBlank(item.getId())) {
                item.setId(ai.indra.core.util.Ids.uuid());
            }
            if (Strings.isBlank(item.getContent())) {
                continue;
            }
            item.setContent(Validation.requireText("content", item.getContent(), 1, MemoryEngine.MAX_CONTENT_LENGTH));
            if (item.getCategory() == null) {
                item.setCategory(MemoryCategory.fromName(MemoryEngine.classify(item.getContent()).name()));
            }
            if (item.getSource() == null) {
                item.setSource(MemorySource.BACKEND_SYNC);
            }
            repository.saveMemory(item);
        }

        // 2. Apply deletions.
        for (int i = 0; i < incoming.getDeletedMemoryIds().size(); i++) {
            String id = incoming.getDeletedMemoryIds().getString(i, null);
            if (id != null) {
                repository.deleteMemory(user.getId(), id, true);
            }
        }

        // 3. Apply incoming tasks.
        for (int i = 0; i < incoming.getTasks().size(); i++) {
            JsonObject doc = incoming.getTasks().getObject(i);
            if (doc == null) {
                continue;
            }
            TaskItem task = TaskItem.fromJson(doc);
            task.setUserId(user.getId());
            if (Strings.isBlank(task.getId())) {
                task.setId(ai.indra.core.util.Ids.uuid());
            }
            if (Strings.isBlank(task.getTitle())) {
                continue;
            }
            repository.saveTask(task);
        }

        // 4. Settings: newer document wins.
        UserSettings serverSettings = repository.loadSettings(user.getId());
        JsonObject incomingSettings = incoming.getSettings();
        if (incomingSettings != null && !incomingSettings.isEmpty()
                && incomingSettings.getLong("updatedAt", 0L) > serverSettings.getUpdatedAt()) {
            incomingSettings.put("userId", user.getId());
            repository.saveSettings(UserSettings.fromJson(incomingSettings));
        }

        // 5. Build the response with everything the client is missing.
        long cursor = incoming.getCursor();
        JsonArray memories = new JsonArray();
        for (MemoryItem item : repository.listMemories(user.getId(), true)) {
            if (cursor <= 0L || item.getUpdatedAt() > cursor) {
                memories.add(item.toJson());
            }
        }
        JsonArray tasks = new JsonArray();
        for (TaskItem task : repository.listTasks(user.getId(), true)) {
            if (cursor <= 0L || task.getUpdatedAt() > cursor) {
                tasks.add(task.toJson());
            }
        }
        long newCursor = System.currentTimeMillis();
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("cursor", Long.valueOf(newCursor));
        response.put("memories", cursor <= 0L ? merged(repository.listMemories(user.getId(), true), incoming.getMemories()) : memories);
        response.put("tasks", tasks);
        response.put("settings", repository.loadSettings(user.getId()).toJson());
        response.put("deletedMemoryIds", incoming.getDeletedMemoryIds());
        response.put("serverTime", Long.valueOf(newCursor));
        return response;
    }

    /** Full-sync path: merge both sides so the client ends up with the union. */
    private JsonArray merged(java.util.List<MemoryItem> serverMemories, JsonArray clientMemories) {
        JsonArray server = new JsonArray();
        for (MemoryItem item : serverMemories) {
            server.add(item.toJson());
        }
        return SyncMerge.mergeMemories(server, clientMemories);
    }
}
