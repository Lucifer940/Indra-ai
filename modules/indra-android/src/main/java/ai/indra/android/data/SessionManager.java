package ai.indra.android.data;

import ai.indra.core.json.JsonObject;
import ai.indra.core.model.UserSettings;
import ai.indra.core.util.Log;

/** Owns the signed-in session and keeps local settings in step with the server. */
public final class SessionManager {

    private final ApiClient api;
    private final Prefs prefs;

    public SessionManager(ApiClient api, Prefs prefs) {
        this.api = api;
        this.prefs = prefs;
    }

    public boolean isSignedIn() {
        return prefs.hasSession();
    }

    public JsonObject user() {
        return prefs.getUser();
    }

    public boolean isAdmin() {
        JsonObject user = user();
        return user != null && "ADMIN".equals(user.getString("role", "USER"));
    }

    /**
     * Starts the app without an account: asks the on-device engine for a device
     * session. Everything (chat, memory, tasks, reminders) works; an email account
     * can be added later from Settings.
     */
    public JsonObject continueLocally() throws ApiClient.ApiException {
        JsonObject response = api.localSession(prefs.getDeviceId(), prefs.getDeviceName());
        store(response);
        return response;
    }

    /** True when the current session is the no-account device session. */
    public boolean isLocalAccount() {
        JsonObject user = user();
        if (user == null) {
            return false;
        }
        String email = user.getString("email", "");
        return email.endsWith("@local.indra") || email.startsWith("device-");
    }

    public JsonObject login(String email, String password) throws ApiClient.ApiException {
        JsonObject response = api.login(email, password);
        store(response);
        return response;
    }

    public JsonObject register(String email, String password, String displayName)
            throws ApiClient.ApiException {
        JsonObject response = api.register(email, password, displayName);
        store(response);
        return response;
    }

    public JsonObject oauth(String provider, String code, String redirectUri, String verifier)
            throws ApiClient.ApiException {
        JsonObject response = api.oauthExchange(provider, code, redirectUri, verifier);
        store(response);
        return response;
    }

    public void logout() {
        api.logout();
        prefs.clearSession();
    }

    private void store(JsonObject response) {
        prefs.saveSession(response.getString("accessToken"), response.getString("refreshToken"),
                response.getObjectOrEmpty("user"));
        JsonObject settings = response.getObjectOrEmpty("settings");
        if (settings.has("personaId")) {
            applySettings(settings);
        }
    }

    /** Mirrors server settings into local prefs so the UI is instant and offline-safe. */
    public void applySettings(JsonObject settings) {
        if (settings == null || settings.isEmpty()) {
            return;
        }
        UserSettings parsed = UserSettings.fromJson(settings);
        prefs.setLocalSettings(parsed.getPersonaId(), parsed.getBrainId(), parsed.getLanguageCode(),
                parsed.getTheme(), parsed.getProvider(), parsed.getModel(), parsed.isMemoryEnabled(),
                parsed.isAutoMemory(), parsed.isAutoSpeak(), parsed.getSpeechRate(), parsed.getSpeechPitch());
        prefs.setWakeWord(parsed.getWakeWord());
        prefs.setWakeWordEnabled(parsed.isWakeWordEnabled());
    }

    /** Payload sent to the backend whenever a local setting changes. */
    public JsonObject settingsPayload() {
        JsonObject body = new JsonObject();
        body.put("personaId", prefs.getPersona());
        body.put("brainId", prefs.getBrain());
        body.put("languageCode", prefs.getLanguage());
        body.put("theme", prefs.getTheme());
        body.put("provider", prefs.getProvider());
        body.put("model", prefs.getModel());
        body.put("memoryEnabled", Boolean.valueOf(prefs.isMemoryEnabled()));
        body.put("autoMemory", Boolean.valueOf(prefs.isAutoMemory()));
        body.put("autoSpeak", Boolean.valueOf(prefs.isAutoSpeak()));
        body.put("speechRate", Double.valueOf(prefs.getSpeechRate()));
        body.put("speechPitch", Double.valueOf(prefs.getSpeechPitch()));
        body.put("wakeWordEnabled", Boolean.valueOf(prefs.isWakeWordEnabled()));
        body.put("wakeWord", prefs.getWakeWord());
        return body;
    }

    /** Best-effort push; failures are logged and retried on the next change. */
    public void pushSettings() {
        try {
            api.saveSettings(settingsPayload());
        } catch (ApiClient.ApiException error) {
            Log.warn("SessionManager", "Settings sync deferred: " + error.getMessage());
        }
    }

    public JsonObject refreshProfile() {
        try {
            JsonObject profile = api.me();
            prefs.saveSession(prefs.getAccessToken(), prefs.getRefreshToken(),
                    profile.getObjectOrEmpty("user"));
            applySettings(profile.getObjectOrEmpty("settings"));
            return profile;
        } catch (ApiClient.ApiException error) {
            Log.warn("SessionManager", "Profile refresh failed: " + error.getMessage());
            return null;
        }
    }
}
