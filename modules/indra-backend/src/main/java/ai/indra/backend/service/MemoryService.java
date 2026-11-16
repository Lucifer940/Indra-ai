package ai.indra.backend.service;

import ai.indra.backend.http.ApiException;
import ai.indra.backend.store.Repository;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.memory.MemoryEngine;
import ai.indra.core.model.MemoryCategory;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.model.User;
import ai.indra.core.security.Validation;
import ai.indra.core.util.Strings;

import java.util.ArrayList;
import java.util.List;

/** Long-term memory endpoints plus the recall helpers used by the chat brain. */
public final class MemoryService {

    private final Repository repository;

    public MemoryService(Repository repository) {
        this.repository = repository;
    }

    public JsonObject list(User user, boolean includeDeleted) {
        List<MemoryItem> items = repository.listMemories(user.getId(), includeDeleted);
        JsonArray array = new JsonArray();
        for (MemoryItem item : items) {
            array.add(item.toJson());
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("memories", array);
        response.put("count", Integer.valueOf(items.size()));
        return response;
    }

    public JsonObject create(User user, JsonObject body) {
        String content = Validation.requireText("content", body.getString("content"), 3, MemoryEngine.MAX_CONTENT_LENGTH);
        if (repository.listMemories(user.getId(), false).size() >= MemoryEngine.MAX_MEMORIES_PER_USER) {
            throw ApiException.conflict("Memory limit reached - delete some memories first");
        }
        MemoryItem duplicate = MemoryEngine.findDuplicate(repository.listMemories(user.getId(), false), content);
        if (duplicate != null) {
            duplicate.setContent(content);
            duplicate.setImportance(Math.max(duplicate.getImportance(), body.getInt("importance", 3)));
            repository.saveMemory(duplicate);
            return single(duplicate, "Memory updated");
        }
        MemoryCategory category = MemoryCategory.fromName(body.getString("category",
                MemoryEngine.classify(content).name()));
        MemoryItem item = MemoryItem.create(user.getId(), content, category,
                ai.indra.core.model.MemorySource.USER_STATED, body.getInt("importance", 3));
        item.setPinned(body.getBoolean("pinned", false));
        repository.saveMemory(item);
        repository.audit(user.getId(), "memory.create", content, null);
        return single(item, "Memory saved");
    }

    public JsonObject update(User user, String id, JsonObject body) {
        MemoryItem item = requireMemory(user, id);
        if (body.has("content")) {
            item.setContent(Validation.requireText("content", body.getString("content"), 3, MemoryEngine.MAX_CONTENT_LENGTH));
        }
        if (body.has("category")) {
            item.setCategory(MemoryCategory.fromName(body.getString("category")));
        }
        if (body.has("importance")) {
            item.setImportance(body.getInt("importance", item.getImportance()));
        }
        if (body.has("pinned")) {
            item.setPinned(body.getBoolean("pinned", item.isPinned()));
        }
        repository.saveMemory(item);
        repository.audit(user.getId(), "memory.update", item.getContent(), null);
        return single(item, "Memory updated");
    }

    public JsonObject delete(User user, String id) {
        MemoryItem item = repository.findMemory(user.getId(), id);
        if (item == null) {
            throw ApiException.notFound("Memory not found");
        }
        repository.deleteMemory(user.getId(), id, true);
        repository.audit(user.getId(), "memory.delete", item.getContent(), null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "Memory deleted");
        return response;
    }

    public JsonObject clear(User user) {
        int before = repository.listMemories(user.getId(), true).size();
        repository.clearMemories(user.getId(), true);
        repository.audit(user.getId(), "memory.clear", before + " memories removed", null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "All memories cleared");
        response.put("deleted", Integer.valueOf(before));
        return response;
    }

    /** Memories most relevant to the current turn. */
    public List<MemoryItem> recall(User user, String query, int limit) {
        List<MemoryItem> all = repository.listMemories(user.getId(), false);
        return MemoryEngine.rank(all, query, limit);
    }

    /** Stores a candidate detected in conversation, de-duplicating first. */
    public MemoryItem remember(User user, MemoryEngine.Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        List<MemoryItem> existing = repository.listMemories(user.getId(), false);
        MemoryItem duplicate = MemoryEngine.findDuplicate(existing, candidate.getContent());
        if (duplicate != null) {
            if (candidate.getImportance() > duplicate.getImportance()) {
                duplicate.setImportance(candidate.getImportance());
                repository.saveMemory(duplicate);
            }
            return duplicate;
        }
        if (existing.size() >= MemoryEngine.MAX_MEMORIES_PER_USER) {
            return null;
        }
        MemoryItem item = candidate.toMemory(user.getId());
        repository.saveMemory(item);
        return item;
    }

    /** Removes memories matching a "forget ..." request; returns the removed items. */
    public List<MemoryItem> forget(User user, String topic) {
        List<MemoryItem> removed = new ArrayList<MemoryItem>();
        if (Strings.isBlank(topic)) {
            return removed;
        }
        for (MemoryItem item : repository.listMemories(user.getId(), false)) {
            if (MemoryEngine.isSimilar(item.getContent(), topic)
                    || Strings.lower(item.getContent()).contains(Strings.lower(topic))) {
                repository.deleteMemory(user.getId(), item.getId(), true);
                removed.add(item);
            }
        }
        if (!removed.isEmpty()) {
            repository.audit(user.getId(), "memory.forget", "topic: " + topic + " (" + removed.size() + ")", null);
        }
        return removed;
    }

    private MemoryItem requireMemory(User user, String id) {
        MemoryItem item = repository.findMemory(user.getId(), id);
        if (item == null) {
            throw ApiException.notFound("Memory not found");
        }
        return item;
    }

    private JsonObject single(MemoryItem item, String message) {
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", message);
        response.put("memory", item.toJson());
        return response;
    }
}
