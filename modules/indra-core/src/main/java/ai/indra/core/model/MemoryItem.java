package ai.indra.core.model;

import ai.indra.core.json.JsonObject;

/** A single long-term memory entry. */
public class MemoryItem {

    private String id;
    private String userId;
    private String content;
    private MemoryCategory category = MemoryCategory.OTHER;
    private MemorySource source = MemorySource.USER_STATED;
    private int importance = 3;
    private boolean pinned;
    private long createdAt;
    private long updatedAt;
    private long deletedAt;

    public MemoryItem() {
    }

    public static MemoryItem create(String userId, String content, MemoryCategory category, MemorySource source, int importance) {
        long now = System.currentTimeMillis();
        MemoryItem item = new MemoryItem();
        item.id = ai.indra.core.util.Ids.uuid();
        item.userId = userId;
        item.content = content;
        item.category = category == null ? MemoryCategory.OTHER : category;
        item.source = source == null ? MemorySource.USER_STATED : source;
        item.importance = Math.max(1, Math.min(5, importance));
        item.createdAt = now;
        item.updatedAt = now;
        return item;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MemoryCategory getCategory() {
        return category;
    }

    public void setCategory(MemoryCategory category) {
        this.category = category == null ? MemoryCategory.OTHER : category;
    }

    public MemorySource getSource() {
        return source;
    }

    public void setSource(MemorySource source) {
        this.source = source == null ? MemorySource.USER_STATED : source;
    }

    public int getImportance() {
        return importance;
    }

    public void setImportance(int importance) {
        this.importance = Math.max(1, Math.min(5, importance));
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(long deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt > 0L;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("id", id);
        json.put("userId", userId);
        json.put("content", content);
        json.put("category", category.name());
        json.put("source", source.name());
        json.put("importance", Integer.valueOf(importance));
        json.put("pinned", Boolean.valueOf(pinned));
        json.put("createdAt", Long.valueOf(createdAt));
        json.put("updatedAt", Long.valueOf(updatedAt));
        json.put("deletedAt", Long.valueOf(deletedAt));
        return json;
    }

    public static MemoryItem fromJson(JsonObject json) {
        MemoryItem item = new MemoryItem();
        item.id = json.getString("id");
        item.userId = json.getString("userId");
        item.content = json.getString("content");
        item.category = MemoryCategory.fromName(json.getString("category", "OTHER"));
        item.source = MemorySource.fromName(json.getString("source", "USER_STATED"));
        item.importance = json.getInt("importance", 3);
        item.pinned = json.getBoolean("pinned", false);
        item.createdAt = json.getLong("createdAt", 0L);
        item.updatedAt = json.getLong("updatedAt", 0L);
        item.deletedAt = json.getLong("deletedAt", 0L);
        return item;
    }
}
