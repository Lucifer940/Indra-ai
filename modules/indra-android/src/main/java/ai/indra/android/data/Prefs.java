package ai.indra.android.data;

import android.content.Context;
import android.content.SharedPreferences;

import ai.indra.core.apikeys.SecretCipher;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

import java.util.UUID;

/**
 * Encrypted preferences.
 *
 * <p>Session tokens, the user profile, the licence key and the server address are
 * stored AES-256-GCM encrypted with a key held by the Android Keystore. Non-secret
 * UI preferences (persona, language, speech rate) are plain values; they reveal
 * nothing about the account.</p>
 */
public final class Prefs implements SecretCipher {

    private static final String STORE = "indra_secure";

    private static final String K_ACCESS = "accessToken";
    private static final String K_REFRESH = "refreshToken";
    private static final String K_USER = "user";
    private static final String K_BASE_URL = "baseUrl";
    private static final String K_DEVICE_ID = "deviceId";
    private static final String K_DEVICE_NAME = "deviceName";
    private static final String K_WAKE_ENABLED = "wakeEnabled";
    private static final String K_WAKE_WORD = "wakeWord";
    private static final String K_PERSONA = "persona";
    private static final String K_BRAIN = "brain";
    private static final String K_LANGUAGE = "language";
    private static final String K_THEME = "theme";
    private static final String K_MEMORY_ENABLED = "memoryEnabled";
    private static final String K_AUTO_MEMORY = "autoMemory";
    private static final String K_AUTO_SPEAK = "autoSpeak";
    private static final String K_SPEECH_RATE = "speechRate";
    private static final String K_SPEECH_PITCH = "speechPitch";
    private static final String K_PROVIDER = "provider";
    private static final String K_MODEL = "model";
    private static final String K_ADMIN = "isAdmin";
    private static final String K_OAUTH_STATE = "oauthState";
    private static final String K_OAUTH_VERIFIER = "oauthVerifier";
    private static final String K_LICENSE = "licenseKey";

    private static final String DEFAULT_BASE_URL = "http://10.0.2.2:8420";

    private final SharedPreferences raw;
    private final SecretCipher cipher;

    public Prefs(Context context) {
        this.raw = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        this.cipher = new KeystoreCipher();
    }

    /** Exposes the Keystore cipher for other local stores (API key vault). */
    public SecretCipher cipher() {
        return cipher;
    }

    @Override
    public String encrypt(String plaintext) {
        return cipher.encrypt(plaintext);
    }

    @Override
    public String decrypt(String cipherText) {
        return cipher.decrypt(cipherText);
    }

    // ---------------------------------------------------------------- helpers

    private void putSecret(String key, String value) {
        if (value == null) {
            raw.edit().remove(key).apply();
            return;
        }
        try {
            raw.edit().putString(key, cipher.encrypt(value)).apply();
        } catch (RuntimeException error) {
            Log.error("Prefs", "Cannot encrypt preference " + key, error);
        }
    }

    private String getSecret(String key, String fallback) {
        String stored = raw.getString(key, null);
        if (stored == null) {
            return fallback;
        }
        try {
            return cipher.decrypt(stored);
        } catch (RuntimeException error) {
            Log.warn("Prefs", "Cannot decrypt preference " + key + " - clearing it");
            raw.edit().remove(key).apply();
            return fallback;
        }
    }

    private String plain(String key, String fallback) {
        return raw.getString(key, fallback);
    }

    // ---------------------------------------------------------------- session

    public String getAccessToken() {
        return getSecret(K_ACCESS, null);
    }

    public String getRefreshToken() {
        return getSecret(K_REFRESH, null);
    }

    public void saveSession(String accessToken, String refreshToken, JsonObject user) {
        putSecret(K_ACCESS, accessToken);
        putSecret(K_REFRESH, refreshToken);
        putSecret(K_USER, user == null ? null : user.toJson());
        raw.edit().putBoolean(K_ADMIN, user != null && "ADMIN".equals(user.getString("role", "USER"))).apply();
    }

    public void clearSession() {
        raw.edit().remove(K_ACCESS).remove(K_REFRESH).remove(K_USER).remove(K_ADMIN)
                .remove(K_OAUTH_STATE).remove(K_OAUTH_VERIFIER).apply();
    }

    public boolean hasSession() {
        return getAccessToken() != null;
    }

    public JsonObject getUser() {
        String json = getSecret(K_USER, null);
        if (json == null) {
            return null;
        }
        try {
            return JsonObject.parse(json);
        } catch (RuntimeException error) {
            return null;
        }
    }

    public boolean isAdmin() {
        return raw.getBoolean(K_ADMIN, false);
    }

    // ----------------------------------------------------------------- server

    public String getBaseUrl() {
        return getSecret(K_BASE_URL, DEFAULT_BASE_URL);
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return;
        }
        putSecret(K_BASE_URL, baseUrl.trim());
    }

    // ----------------------------------------------------------------- device

    public String getDeviceId() {
        String stored = getSecret(K_DEVICE_ID, null);
        if (stored != null && !stored.isEmpty()) {
            return stored;
        }
        String generated = UUID.randomUUID().toString();
        putSecret(K_DEVICE_ID, generated);
        return generated;
    }

    public String getDeviceName() {
        return plain(K_DEVICE_NAME, null);
    }

    public void setDeviceName(String name) {
        raw.edit().putString(K_DEVICE_NAME, name).apply();
    }

    // ------------------------------------------------------------------- ui

    public String getPersona() {
        return plain(K_PERSONA, "friendly");
    }

    public String getBrain() {
        return plain(K_BRAIN, "core");
    }

    public String getLanguage() {
        return plain(K_LANGUAGE, "en");
    }

    public String getTheme() {
        return plain(K_THEME, "dark");
    }

    public String getProvider() {
        return plain(K_PROVIDER, "offline");
    }

    public String getModel() {
        return plain(K_MODEL, "");
    }

    public boolean isMemoryEnabled() {
        return raw.getBoolean(K_MEMORY_ENABLED, true);
    }

    public boolean isAutoMemory() {
        return raw.getBoolean(K_AUTO_MEMORY, true);
    }

    public boolean isAutoSpeak() {
        return raw.getBoolean(K_AUTO_SPEAK, true);
    }

    public float getSpeechRate() {
        return raw.getFloat(K_SPEECH_RATE, 1.0f);
    }

    public float getSpeechPitch() {
        return raw.getFloat(K_SPEECH_PITCH, 1.05f);
    }

    public void setLocalSettings(String persona, String brain, String language, String theme,
                                 String provider, String model, boolean memoryEnabled, boolean autoMemory,
                                 boolean autoSpeak, float rate, float pitch) {
        raw.edit()
                .putString(K_PERSONA, persona)
                .putString(K_BRAIN, brain)
                .putString(K_LANGUAGE, language)
                .putString(K_THEME, theme)
                .putString(K_PROVIDER, provider)
                .putString(K_MODEL, model)
                .putBoolean(K_MEMORY_ENABLED, memoryEnabled)
                .putBoolean(K_AUTO_MEMORY, autoMemory)
                .putBoolean(K_AUTO_SPEAK, autoSpeak)
                .putFloat(K_SPEECH_RATE, Math.max(0.5f, Math.min(2.0f, rate)))
                .putFloat(K_SPEECH_PITCH, Math.max(0.5f, Math.min(2.0f, pitch)))
                .apply();
    }

    // ------------------------------------------------------------- wake word

    public boolean isWakeWordEnabled() {
        return raw.getBoolean(K_WAKE_ENABLED, false);
    }

    public void setWakeWordEnabled(boolean enabled) {
        raw.edit().putBoolean(K_WAKE_ENABLED, enabled).apply();
    }

    public String getWakeWord() {
        return plain(K_WAKE_WORD, "indra");
    }

    public void setWakeWord(String word) {
        raw.edit().putString(K_WAKE_WORD, word == null || word.trim().isEmpty() ? "indra" : word.trim()).apply();
    }

    // -------------------------------------------------------------- licence

    public String getLicenseKey() {
        return getSecret(K_LICENSE, "");
    }

    public void setLicenseKey(String key) {
        putSecret(K_LICENSE, key == null || key.trim().isEmpty() ? null : key.trim());
    }

    // ----------------------------------------------------------------- oauth

    public void setOauth(String state, String verifier) {
        putSecret(K_OAUTH_STATE, state);
        putSecret(K_OAUTH_VERIFIER, verifier);
    }

    public String getOauthState() {
        return getSecret(K_OAUTH_STATE, null);
    }

    public String getOauthVerifier() {
        return getSecret(K_OAUTH_VERIFIER, null);
    }

    public void clearOauth() {
        raw.edit().remove(K_OAUTH_STATE).remove(K_OAUTH_VERIFIER).apply();
    }
}
