package ai.indra.core.model;

import ai.indra.core.json.JsonObject;

/** Chat thread metadata. */
public class Conversation {

    private String id;
    private String userId;
    private String title;
    private long createdAt;
    private long updatedAt;
    private int messageCount;
    private boolean archived;

    public Conversation() {
    }

    public static Conversation create(String userId, String title) {
        long now = System.currentTimeMillis();
        Conversation conversation = new Conversation();
        conversation.id = ai.indra.core.util.Ids.uuid();
        conversation.userId = userId;
        conversation.title = title;
        conversation.createdAt = now;
        conversation.updatedAt = now;
        return conversation;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("id", id);
        json.put("userId", userId);
        json.put("title", title);
        json.put("createdAt", Long.valueOf(createdAt));
        json.put("updatedAt", Long.valueOf(updatedAt));
        json.put("messageCount", Integer.valueOf(messageCount));
        json.put("archived", Boolean.valueOf(archived));
        return json;
    }

    public static Conversation fromJson(JsonObject json) {
        Conversation conversation = new Conversation();
        conversation.id = json.getString("id");
        conversation.userId = json.getString("userId");
        conversation.title = json.getString("title");
        conversation.createdAt = json.getLong("createdAt", 0L);
        conversation.updatedAt = json.getLong("updatedAt", 0L);
        conversation.messageCount = json.getInt("messageCount", 0);
        conversation.archived = json.getBoolean("archived", false);
        return conversation;
    }
}
