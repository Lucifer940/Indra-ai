package ai.indra.core.model;

import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Times;

/** An INDRA AI account. Password hashes are never exposed by {@link #toJson()}. */
public class User {

    private String id;
    private String email;
    private String displayName;
    private String passwordHash;
    private Role role = Role.USER;
    private AuthProvider provider = AuthProvider.LOCAL;
    private String providerSubject;
    private String photoUrl;
    private String licenseKey;
    private boolean emailVerified;
    private boolean disabled;
    private long createdAt;
    private long updatedAt;
    private long lastLoginAt;

    public User() {
    }

    public static User newUser(String email, String displayName, String passwordHash) {
        long now = System.currentTimeMillis();
        User user = new User();
        user.id = ai.indra.core.util.Ids.uuid();
        user.email = email;
        user.displayName = displayName;
        user.passwordHash = passwordHash;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role == null ? Role.USER : role;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public void setProvider(AuthProvider provider) {
        this.provider = provider == null ? AuthProvider.LOCAL : provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public void setProviderSubject(String providerSubject) {
        this.providerSubject = providerSubject;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public void setLicenseKey(String licenseKey) {
        this.licenseKey = licenseKey;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
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

    public long getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(long lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /** Safe profile representation - never includes the password hash. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("id", id);
        json.put("email", email);
        json.put("displayName", displayName);
        json.put("role", role.name());
        json.put("provider", provider.name());
        json.put("photoUrl", photoUrl);
        json.put("licenseKey", licenseKey);
        json.put("emailVerified", Boolean.valueOf(emailVerified));
        json.put("disabled", Boolean.valueOf(disabled));
        json.put("createdAt", Long.valueOf(createdAt));
        json.put("updatedAt", Long.valueOf(updatedAt));
        json.put("lastLoginAt", Long.valueOf(lastLoginAt));
        json.put("createdAtIso", Times.toIso(createdAt));
        return json;
    }

    public static User fromJson(JsonObject json) {
        User user = new User();
        user.id = json.getString("id");
        user.email = json.getString("email");
        user.displayName = json.getString("displayName");
        user.passwordHash = json.getString("passwordHash");
        user.role = Role.fromName(json.getString("role", "USER"));
        user.provider = AuthProvider.fromName(json.getString("provider", "LOCAL"));
        user.providerSubject = json.getString("providerSubject");
        user.photoUrl = json.getString("photoUrl");
        user.licenseKey = json.getString("licenseKey");
        user.emailVerified = json.getBoolean("emailVerified", false);
        user.disabled = json.getBoolean("disabled", false);
        user.createdAt = json.getLong("createdAt", 0L);
        user.updatedAt = json.getLong("updatedAt", 0L);
        user.lastLoginAt = json.getLong("lastLoginAt", 0L);
        return user;
    }
}
