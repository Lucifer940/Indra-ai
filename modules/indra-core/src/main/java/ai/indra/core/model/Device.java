package ai.indra.core.model;

import ai.indra.core.json.JsonObject;

/** A paired client device (phone or PC). */
public class Device {

    private String id;
    private String userId;
    private String name;
    private Platform platform = Platform.ANDROID;
    private String publicKey;
    private String appVersion;
    private long pairedAt;
    private long lastSeenAt;
    private boolean revoked;

    public Device() {
    }

    public static Device create(String userId, String name, Platform platform, String publicKey) {
        long now = System.currentTimeMillis();
        Device device = new Device();
        device.id = ai.indra.core.util.Ids.uuid();
        device.userId = userId;
        device.name = name;
        device.platform = platform == null ? Platform.ANDROID : platform;
        device.publicKey = publicKey;
        device.pairedAt = now;
        device.lastSeenAt = now;
        return device;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform == null ? Platform.ANDROID : platform;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public long getPairedAt() {
        return pairedAt;
    }

    public void setPairedAt(long pairedAt) {
        this.pairedAt = pairedAt;
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("id", id);
        json.put("userId", userId);
        json.put("name", name);
        json.put("platform", platform.name());
        json.put("appVersion", appVersion);
        json.put("pairedAt", Long.valueOf(pairedAt));
        json.put("lastSeenAt", Long.valueOf(lastSeenAt));
        json.put("revoked", Boolean.valueOf(revoked));
        return json;
    }

    public static Device fromJson(JsonObject json) {
        Device device = new Device();
        device.id = json.getString("id");
        device.userId = json.getString("userId");
        device.name = json.getString("name");
        device.platform = Platform.fromName(json.getString("platform", "ANDROID"));
        device.publicKey = json.getString("publicKey");
        device.appVersion = json.getString("appVersion");
        device.pairedAt = json.getLong("pairedAt", 0L);
        device.lastSeenAt = json.getLong("lastSeenAt", 0L);
        device.revoked = json.getBoolean("revoked", false);
        return device;
    }
}
