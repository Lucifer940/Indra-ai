package ai.indra.core.sync;

import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;

/** Payload exchanged between the phone, the PC and the backend. */
public class SyncEnvelope {

    private String deviceId;
    private String userId;
    private long cursor;
    private long generatedAt;
    private JsonArray memories = new JsonArray();
    private JsonArray tasks = new JsonArray();
    private JsonObject settings = new JsonObject();
    private JsonArray deletedMemoryIds = new JsonArray();
    private String appVersion;
    private String platform;

    public SyncEnvelope() {
    }

    public static SyncEnvelope empty(String userId, String deviceId) {
        SyncEnvelope envelope = new SyncEnvelope();
        envelope.userId = userId;
        envelope.deviceId = deviceId;
        envelope.generatedAt = System.currentTimeMillis();
        return envelope;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public SyncEnvelope setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public SyncEnvelope setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    /** Server change cursor: everything newer than this is returned. */
    public long getCursor() {
        return cursor;
    }

    public SyncEnvelope setCursor(long cursor) {
        this.cursor = cursor;
        return this;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    public SyncEnvelope setGeneratedAt(long generatedAt) {
        this.generatedAt = generatedAt;
        return this;
    }

    public JsonArray getMemories() {
        return memories;
    }

    public SyncEnvelope setMemories(JsonArray memories) {
        this.memories = memories == null ? new JsonArray() : memories;
        return this;
    }

    public JsonArray getTasks() {
        return tasks;
    }

    public SyncEnvelope setTasks(JsonArray tasks) {
        this.tasks = tasks == null ? new JsonArray() : tasks;
        return this;
    }

    public JsonObject getSettings() {
        return settings;
    }

    public SyncEnvelope setSettings(JsonObject settings) {
        this.settings = settings == null ? new JsonObject() : settings;
        return this;
    }

    public JsonArray getDeletedMemoryIds() {
        return deletedMemoryIds;
    }

    public SyncEnvelope setDeletedMemoryIds(JsonArray deletedMemoryIds) {
        this.deletedMemoryIds = deletedMemoryIds == null ? new JsonArray() : deletedMemoryIds;
        return this;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public SyncEnvelope setAppVersion(String appVersion) {
        this.appVersion = appVersion;
        return this;
    }

    public String getPlatform() {
        return platform;
    }

    public SyncEnvelope setPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("deviceId", deviceId);
        json.put("userId", userId);
        json.put("cursor", Long.valueOf(cursor));
        json.put("generatedAt", Long.valueOf(generatedAt));
        json.put("memories", memories);
        json.put("tasks", tasks);
        json.put("settings", settings);
        json.put("deletedMemoryIds", deletedMemoryIds);
        json.put("appVersion", appVersion);
        json.put("platform", platform);
        return json;
    }

    public static SyncEnvelope fromJson(JsonObject json) {
        SyncEnvelope envelope = new SyncEnvelope();
        if (json == null) {
            return envelope;
        }
        envelope.deviceId = json.getString("deviceId");
        envelope.userId = json.getString("userId");
        envelope.cursor = json.getLong("cursor", 0L);
        envelope.generatedAt = json.getLong("generatedAt", 0L);
        envelope.memories = json.getArrayOrEmpty("memories");
        envelope.tasks = json.getArrayOrEmpty("tasks");
        envelope.settings = json.getObjectOrEmpty("settings");
        envelope.deletedMemoryIds = json.getArrayOrEmpty("deletedMemoryIds");
        envelope.appVersion = json.getString("appVersion");
        envelope.platform = json.getString("platform");
        return envelope;
    }
}
