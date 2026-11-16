package ai.indra.core.model;

import ai.indra.core.json.JsonObject;

/** Per-user preferences; synchronised between Android and Windows. */
public class UserSettings {

    private String userId;
    private String languageCode = "en";
    private String personaId = "friendly";
    private String brainId = "core";
    private String provider = "openai";
    private String model = "gpt-4o-mini";
    private String ttsVoice;
    private float speechRate = 1.0f;
    private float speechPitch = 1.0f;
    private boolean autoSpeak = true;
    private boolean memoryEnabled = true;
    private boolean autoMemory = false;
    private boolean wakeWordEnabled = false;
    private String wakeWord = "indra";
    private boolean streamingEnabled = true;
    private boolean notificationsEnabled = true;
    private boolean syncEnabled = true;
    private String theme = "system";
    private long updatedAt;

    public UserSettings() {
    }

    public static UserSettings defaults(String userId) {
        UserSettings settings = new UserSettings();
        settings.userId = userId;
        settings.updatedAt = System.currentTimeMillis();
        return settings;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getPersonaId() {
        return personaId;
    }

    public void setPersonaId(String personaId) {
        this.personaId = personaId;
    }

    public String getBrainId() {
        return brainId;
    }

    public void setBrainId(String brainId) {
        this.brainId = brainId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTtsVoice() {
        return ttsVoice;
    }

    public void setTtsVoice(String ttsVoice) {
        this.ttsVoice = ttsVoice;
    }

    public float getSpeechRate() {
        return speechRate;
    }

    public void setSpeechRate(float speechRate) {
        this.speechRate = Math.max(0.5f, Math.min(2.0f, speechRate));
    }

    public float getSpeechPitch() {
        return speechPitch;
    }

    public void setSpeechPitch(float speechPitch) {
        this.speechPitch = Math.max(0.5f, Math.min(2.0f, speechPitch));
    }

    public boolean isAutoSpeak() {
        return autoSpeak;
    }

    public void setAutoSpeak(boolean autoSpeak) {
        this.autoSpeak = autoSpeak;
    }

    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    public void setMemoryEnabled(boolean memoryEnabled) {
        this.memoryEnabled = memoryEnabled;
    }

    public boolean isAutoMemory() {
        return autoMemory;
    }

    public void setAutoMemory(boolean autoMemory) {
        this.autoMemory = autoMemory;
    }

    public boolean isWakeWordEnabled() {
        return wakeWordEnabled;
    }

    public void setWakeWordEnabled(boolean wakeWordEnabled) {
        this.wakeWordEnabled = wakeWordEnabled;
    }

    public String getWakeWord() {
        return wakeWord;
    }

    public void setWakeWord(String wakeWord) {
        this.wakeWord = wakeWord;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    public void setStreamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Language getLanguage() {
        return Language.fromCode(languageCode);
    }

    public Persona getPersona() {
        return Persona.fromId(personaId);
    }

    public BrainProfile getBrain() {
        return BrainProfile.fromId(brainId);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("userId", userId);
        json.put("languageCode", languageCode);
        json.put("personaId", personaId);
        json.put("brainId", brainId);
        json.put("provider", provider);
        json.put("model", model);
        json.put("ttsVoice", ttsVoice);
        json.put("speechRate", Double.valueOf(speechRate));
        json.put("speechPitch", Double.valueOf(speechPitch));
        json.put("autoSpeak", Boolean.valueOf(autoSpeak));
        json.put("memoryEnabled", Boolean.valueOf(memoryEnabled));
        json.put("autoMemory", Boolean.valueOf(autoMemory));
        json.put("wakeWordEnabled", Boolean.valueOf(wakeWordEnabled));
        json.put("wakeWord", wakeWord);
        json.put("streamingEnabled", Boolean.valueOf(streamingEnabled));
        json.put("notificationsEnabled", Boolean.valueOf(notificationsEnabled));
        json.put("syncEnabled", Boolean.valueOf(syncEnabled));
        json.put("theme", theme);
        json.put("updatedAt", Long.valueOf(updatedAt));
        return json;
    }

    public static UserSettings fromJson(JsonObject json) {
        UserSettings settings = new UserSettings();
        settings.userId = json.getString("userId");
        settings.languageCode = json.getString("languageCode", "en");
        settings.personaId = json.getString("personaId", "friendly");
        settings.brainId = json.getString("brainId", "core");
        settings.provider = json.getString("provider", "openai");
        settings.model = json.getString("model", "gpt-4o-mini");
        settings.ttsVoice = json.getString("ttsVoice");
        settings.speechRate = (float) json.getDouble("speechRate", 1.0d);
        settings.speechPitch = (float) json.getDouble("speechPitch", 1.0d);
        settings.autoSpeak = json.getBoolean("autoSpeak", true);
        settings.memoryEnabled = json.getBoolean("memoryEnabled", true);
        settings.autoMemory = json.getBoolean("autoMemory", false);
        settings.wakeWordEnabled = json.getBoolean("wakeWordEnabled", false);
        settings.wakeWord = json.getString("wakeWord", "indra");
        settings.streamingEnabled = json.getBoolean("streamingEnabled", true);
        settings.notificationsEnabled = json.getBoolean("notificationsEnabled", true);
        settings.syncEnabled = json.getBoolean("syncEnabled", true);
        settings.theme = json.getString("theme", "system");
        settings.updatedAt = json.getLong("updatedAt", 0L);
        return settings;
    }
}
