package ai.indra.core.config;

import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.BrainProfile;
import ai.indra.core.model.Persona;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-published application configuration. An administrator edits this once
 * and every client picks it up on its next poll / launch, which is how settings,
 * announcements, persona availability and maintenance windows stay in sync
 * across all users without shipping a new APK.
 */
public final class RemoteConfig {

    private long revision;
    private String latestVersion = "1.1.0";
    private String minVersion = "1.0.0";
    private String updateUrl = "";
    private String announcement = "";
    private String announcementId = "";
    private String maintenanceMessage = "";
    private boolean memoryEnabled = true;
    private boolean wakeWordEnabled = true;
    private boolean registrationOpen = true;
    private boolean googleLoginEnabled = false;
    private boolean githubLoginEnabled = false;
    private List<String> allowedPersonas = Persona.ids();
    private List<String> allowedBrains = BrainProfile.ids();
    private List<String> enabledProviders = ai.indra.core.ai.ProviderCatalog.ids();
    private JsonObject features = new JsonObject();
    private long updatedAt;

    public RemoteConfig() {
    }

    public static RemoteConfig defaults() {
        RemoteConfig config = new RemoteConfig();
        config.updatedAt = System.currentTimeMillis();
        JsonObject features = new JsonObject();
        features.put("whiteboard", Boolean.TRUE);
        features.put("imageGeneration", Boolean.TRUE);
        features.put("computerControl", Boolean.TRUE);
        features.put("deviceSync", Boolean.TRUE);
        features.put("streaming", Boolean.TRUE);
        config.features = features;
        return config;
    }

    public long getRevision() {
        return revision;
    }

    public RemoteConfig setRevision(long revision) {
        this.revision = revision;
        return this;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public RemoteConfig setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
        return this;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public RemoteConfig setMinVersion(String minVersion) {
        this.minVersion = minVersion;
        return this;
    }

    public String getUpdateUrl() {
        return updateUrl;
    }

    public RemoteConfig setUpdateUrl(String updateUrl) {
        this.updateUrl = updateUrl;
        return this;
    }

    public String getAnnouncement() {
        return announcement;
    }

    public RemoteConfig setAnnouncement(String announcement) {
        this.announcement = announcement;
        return this;
    }

    public String getAnnouncementId() {
        return announcementId;
    }

    public RemoteConfig setAnnouncementId(String announcementId) {
        this.announcementId = announcementId;
        return this;
    }

    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    public RemoteConfig setMaintenanceMessage(String maintenanceMessage) {
        this.maintenanceMessage = maintenanceMessage;
        return this;
    }

    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    public RemoteConfig setMemoryEnabled(boolean memoryEnabled) {
        this.memoryEnabled = memoryEnabled;
        return this;
    }

    public boolean isWakeWordEnabled() {
        return wakeWordEnabled;
    }

    public RemoteConfig setWakeWordEnabled(boolean wakeWordEnabled) {
        this.wakeWordEnabled = wakeWordEnabled;
        return this;
    }

    public boolean isRegistrationOpen() {
        return registrationOpen;
    }

    public RemoteConfig setRegistrationOpen(boolean registrationOpen) {
        this.registrationOpen = registrationOpen;
        return this;
    }

    public boolean isGoogleLoginEnabled() {
        return googleLoginEnabled;
    }

    public RemoteConfig setGoogleLoginEnabled(boolean googleLoginEnabled) {
        this.googleLoginEnabled = googleLoginEnabled;
        return this;
    }

    public boolean isGithubLoginEnabled() {
        return githubLoginEnabled;
    }

    public RemoteConfig setGithubLoginEnabled(boolean githubLoginEnabled) {
        this.githubLoginEnabled = githubLoginEnabled;
        return this;
    }

    public List<String> getAllowedPersonas() {
        return allowedPersonas;
    }

    public RemoteConfig setAllowedPersonas(List<String> allowedPersonas) {
        this.allowedPersonas = allowedPersonas == null ? new ArrayList<String>() : allowedPersonas;
        return this;
    }

    public List<String> getAllowedBrains() {
        return allowedBrains;
    }

    public RemoteConfig setAllowedBrains(List<String> allowedBrains) {
        this.allowedBrains = allowedBrains == null ? new ArrayList<String>() : allowedBrains;
        return this;
    }

    public List<String> getEnabledProviders() {
        return enabledProviders;
    }

    public RemoteConfig setEnabledProviders(List<String> enabledProviders) {
        this.enabledProviders = enabledProviders == null ? new ArrayList<String>() : enabledProviders;
        return this;
    }

    public JsonObject getFeatures() {
        return features;
    }

    public RemoteConfig setFeatures(JsonObject features) {
        this.features = features == null ? new JsonObject() : features;
        return this;
    }

    public boolean isFeatureEnabled(String key) {
        return features.getBoolean(key, false);
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public RemoteConfig setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    /** True when the client must update before continuing. */
    public boolean requiresUpdate(String clientVersion) {
        return compareVersions(clientVersion, minVersion) < 0;
    }

    public boolean hasNewerVersion(String clientVersion) {
        return compareVersions(clientVersion, latestVersion) < 0;
    }

    public boolean isMaintenance() {
        return maintenanceMessage != null && !maintenanceMessage.trim().isEmpty();
    }

    /** Numeric comparison of dotted versions; missing parts count as 0. */
    public static int compareVersions(String left, String right) {
        String[] a = (left == null ? "0" : left).trim().split("[.\\-+]");
        String[] b = (right == null ? "0" : right).trim().split("[.\\-+]");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? parsePart(a[i]) : 0;
            int y = i < b.length ? parsePart(b[i]) : 0;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return 0;
    }

    private static int parsePart(String value) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        if (digits.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("revision", Long.valueOf(revision));
        json.put("latestVersion", latestVersion);
        json.put("minVersion", minVersion);
        json.put("updateUrl", updateUrl);
        json.put("announcement", announcement);
        json.put("announcementId", announcementId);
        json.put("maintenanceMessage", maintenanceMessage);
        json.put("memoryEnabled", Boolean.valueOf(memoryEnabled));
        json.put("wakeWordEnabled", Boolean.valueOf(wakeWordEnabled));
        json.put("registrationOpen", Boolean.valueOf(registrationOpen));
        json.put("googleLoginEnabled", Boolean.valueOf(googleLoginEnabled));
        json.put("githubLoginEnabled", Boolean.valueOf(githubLoginEnabled));
        json.put("allowedPersonas", toArray(allowedPersonas));
        json.put("allowedBrains", toArray(allowedBrains));
        json.put("enabledProviders", toArray(enabledProviders));
        json.put("features", features);
        json.put("updatedAt", Long.valueOf(updatedAt));
        return json;
    }

    private static JsonArray toArray(List<String> values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (String value : values) {
                array.add(value);
            }
        }
        return array;
    }

    public static RemoteConfig fromJson(JsonObject json) {
        RemoteConfig config = defaults();
        if (json == null) {
            return config;
        }
        config.revision = json.getLong("revision", 0L);
        config.latestVersion = json.getString("latestVersion", config.latestVersion);
        config.minVersion = json.getString("minVersion", config.minVersion);
        config.updateUrl = json.getString("updateUrl", "");
        config.announcement = json.getString("announcement", "");
        config.announcementId = json.getString("announcementId", "");
        config.maintenanceMessage = json.getString("maintenanceMessage", "");
        config.memoryEnabled = json.getBoolean("memoryEnabled", true);
        config.wakeWordEnabled = json.getBoolean("wakeWordEnabled", true);
        config.registrationOpen = json.getBoolean("registrationOpen", true);
        config.googleLoginEnabled = json.getBoolean("googleLoginEnabled", false);
        config.githubLoginEnabled = json.getBoolean("githubLoginEnabled", false);
        config.allowedPersonas = json.getArrayOrEmpty("allowedPersonas").asStringList();
        config.allowedBrains = json.getArrayOrEmpty("allowedBrains").asStringList();
        config.enabledProviders = json.getArrayOrEmpty("enabledProviders").asStringList();
        config.features = json.getObjectOrEmpty("features");
        config.updatedAt = json.getLong("updatedAt", 0L);
        return config;
    }
}
