package ai.indra.core.model;

import ai.indra.core.json.JsonObject;

/** An issued INDRA AI licence key. */
public class LicenseRecord {

    private String key;
    private String plan = "personal";
    private String issuedBy;
    private long issuedAt;
    private long expiresAt;
    private String boundUserId;
    private String boundEmail;
    private boolean revoked;
    private int maxDevices = 3;
    private String notes;

    public LicenseRecord() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(long issuedAt) {
        this.issuedAt = issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getBoundUserId() {
        return boundUserId;
    }

    public void setBoundUserId(String boundUserId) {
        this.boundUserId = boundUserId;
    }

    public String getBoundEmail() {
        return boundEmail;
    }

    public void setBoundEmail(String boundEmail) {
        this.boundEmail = boundEmail;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public int getMaxDevices() {
        return maxDevices;
    }

    public void setMaxDevices(int maxDevices) {
        this.maxDevices = maxDevices;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    /** True when the licence may be activated at this instant. */
    public boolean isUsable(long now) {
        return !revoked && (expiresAt <= 0L || now < expiresAt);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("key", key);
        json.put("plan", plan);
        json.put("issuedBy", issuedBy);
        json.put("issuedAt", Long.valueOf(issuedAt));
        json.put("expiresAt", Long.valueOf(expiresAt));
        json.put("boundUserId", boundUserId);
        json.put("boundEmail", boundEmail);
        json.put("revoked", Boolean.valueOf(revoked));
        json.put("maxDevices", Integer.valueOf(maxDevices));
        json.put("notes", notes);
        return json;
    }

    public static LicenseRecord fromJson(JsonObject json) {
        LicenseRecord record = new LicenseRecord();
        record.key = json.getString("key");
        record.plan = json.getString("plan", "personal");
        record.issuedBy = json.getString("issuedBy");
        record.issuedAt = json.getLong("issuedAt", 0L);
        record.expiresAt = json.getLong("expiresAt", 0L);
        record.boundUserId = json.getString("boundUserId");
        record.boundEmail = json.getString("boundEmail");
        record.revoked = json.getBoolean("revoked", false);
        record.maxDevices = json.getInt("maxDevices", 3);
        record.notes = json.getString("notes");
        return record;
    }
}
