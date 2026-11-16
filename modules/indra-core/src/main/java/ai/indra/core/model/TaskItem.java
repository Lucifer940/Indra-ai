package ai.indra.core.model;

import ai.indra.core.json.JsonObject;

/** A task, reminder or alarm. Reminders are tasks with a non-zero remindAt. */
public class TaskItem {

    private String id;
    private String userId;
    private String title;
    private String notes;
    private long dueAt;
    private long remindAt;
    private Recurrence recurrence = Recurrence.NONE;
    private boolean completed;
    private long completedAt;
    private int priority = 2;
    private long notifiedAt;
    private long createdAt;
    private long updatedAt;
    private String deviceId;
    private String conversationId;

    public TaskItem() {
    }

    public static TaskItem create(String userId, String title) {
        long now = System.currentTimeMillis();
        TaskItem task = new TaskItem();
        task.id = ai.indra.core.util.Ids.uuid();
        task.userId = userId;
        task.title = title;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public long getDueAt() {
        return dueAt;
    }

    public void setDueAt(long dueAt) {
        this.dueAt = dueAt;
    }

    public long getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(long remindAt) {
        this.remindAt = remindAt;
    }

    public Recurrence getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(Recurrence recurrence) {
        this.recurrence = recurrence == null ? Recurrence.NONE : recurrence;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(1, Math.min(3, priority));
    }

    public long getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(long notifiedAt) {
        this.notifiedAt = notifiedAt;
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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public boolean isReminder() {
        return remindAt > 0L;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("id", id);
        json.put("userId", userId);
        json.put("title", title);
        json.put("notes", notes);
        json.put("dueAt", Long.valueOf(dueAt));
        json.put("remindAt", Long.valueOf(remindAt));
        json.put("recurrence", recurrence.getId());
        json.put("completed", Boolean.valueOf(completed));
        json.put("completedAt", Long.valueOf(completedAt));
        json.put("priority", Integer.valueOf(priority));
        json.put("notifiedAt", Long.valueOf(notifiedAt));
        json.put("createdAt", Long.valueOf(createdAt));
        json.put("updatedAt", Long.valueOf(updatedAt));
        json.put("deviceId", deviceId);
        json.put("conversationId", conversationId);
        return json;
    }

    public static TaskItem fromJson(JsonObject json) {
        TaskItem task = new TaskItem();
        task.id = json.getString("id");
        task.userId = json.getString("userId");
        task.title = json.getString("title");
        task.notes = json.getString("notes");
        task.dueAt = json.getLong("dueAt", 0L);
        task.remindAt = json.getLong("remindAt", 0L);
        task.recurrence = Recurrence.fromId(json.getString("recurrence", "none"));
        task.completed = json.getBoolean("completed", false);
        task.completedAt = json.getLong("completedAt", 0L);
        task.priority = json.getInt("priority", 2);
        task.notifiedAt = json.getLong("notifiedAt", 0L);
        task.createdAt = json.getLong("createdAt", 0L);
        task.updatedAt = json.getLong("updatedAt", 0L);
        task.deviceId = json.getString("deviceId");
        task.conversationId = json.getString("conversationId");
        return task;
    }
}
