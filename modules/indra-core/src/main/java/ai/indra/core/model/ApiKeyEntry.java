package ai.indra.core.model;

import ai.indra.core.json.JsonObject;

/**
 * Metadata for one AI provider key. The secret itself is never kept here - it is
 * stored encrypted by {@link ai.indra.core.apikeys.ApiKeyVault} and this object
 * only carries the masked preview plus rotation/health state.
 */
public class ApiKeyEntry {

    private String id;
    private String ownerId;
    private String provider;
    private String label;
    private String maskedKey;
    private boolean active = true;
    private boolean permanent = true;
    private long createdAt;
    private long lastUsedAt;
    private int failureCount;
    private long cooldownUntil;

    public ApiKeyEntry() {
    }

    public static ApiKeyEntry create(String ownerId, String provider, String label, String maskedKey) {
        ApiKeyEntry entry = new ApiKeyEntry();
        entry.id = ai.indra.core.util.Ids.uuid();
        entry.ownerId = ownerId;
        entry.provider = provider;
        entry.label = label;
        entry.maskedKey = maskedKey;
        entry.createdAt = System.currentTimeMillis();
        return entry;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getMaskedKey() {
        return maskedKey;
    }

    public void setMaskedKey(String maskedKey) {
        this.maskedKey = maskedKey;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(long lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public boolean isUsable(long now) {
        return active && now >= cooldownUntil;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("id", id);
        json.put("ownerId", ownerId);
        json.put("provider", provider);
        json.put("label", label);
        json.put("maskedKey", maskedKey);
        json.put("active", Boolean.valueOf(active));
        json.put("permanent", Boolean.valueOf(permanent));
        json.put("createdAt", Long.valueOf(createdAt));
        json.put("lastUsedAt", Long.valueOf(lastUsedAt));
        json.put("failureCount", Integer.valueOf(failureCount));
        json.put("cooldownUntil", Long.valueOf(cooldownUntil));
        return json;
    }

    public static ApiKeyEntry fromJson(JsonObject json) {
        ApiKeyEntry entry = new ApiKeyEntry();
        entry.id = json.getString("id");
        entry.ownerId = json.getString("ownerId");
        entry.provider = json.getString("provider");
        entry.label = json.getString("label");
        entry.maskedKey = json.getString("maskedKey");
        entry.active = json.getBoolean("active", true);
        entry.permanent = json.getBoolean("permanent", true);
        entry.createdAt = json.getLong("createdAt", 0L);
        entry.lastUsedAt = json.getLong("lastUsedAt", 0L);
        entry.failureCount = json.getInt("failureCount", 0);
        entry.cooldownUntil = json.getLong("cooldownUntil", 0L);
        return entry;
    }
}
