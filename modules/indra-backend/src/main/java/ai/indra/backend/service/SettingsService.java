package ai.indra.backend.service;

import ai.indra.backend.store.Repository;
import ai.indra.core.ai.ProviderCatalog;
import ai.indra.core.config.RemoteConfig;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.BrainProfile;
import ai.indra.core.model.Language;
import ai.indra.core.model.Persona;
import ai.indra.core.model.User;
import ai.indra.core.model.UserSettings;
import ai.indra.core.security.Validation;

import java.util.List;

/** Per-user settings, validated against the administrator's remote config. */
public final class SettingsService {

    private final Repository repository;

    public SettingsService(Repository repository) {
        this.repository = repository;
    }

    public JsonObject get(User user) {
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("settings", repository.loadSettings(user.getId()).toJson());
        return response;
    }

    public JsonObject update(User user, JsonObject body) {
        UserSettings settings = repository.loadSettings(user.getId());
        RemoteConfig remote = repository.loadConfig();

        if (body.has("languageCode")) {
            settings.setLanguageCode(Language.fromCode(body.getString("languageCode")).getCode());
        }
        if (body.has("personaId")) {
            String personaId = Persona.fromId(body.getString("personaId")).getId();
            if (!remote.getAllowedPersonas().contains(personaId)) {
                throw ai.indra.backend.http.ApiException.forbidden("That personality is disabled by the administrator");
            }
            settings.setPersonaId(personaId);
        }
        if (body.has("brainId")) {
            String brainId = BrainProfile.fromId(body.getString("brainId")).getId();
            if (!remote.getAllowedBrains().contains(brainId)) {
                throw ai.indra.backend.http.ApiException.forbidden("That brain mode is disabled by the administrator");
            }
            settings.setBrainId(brainId);
        }
        if (body.has("provider")) {
            String provider = body.getString("provider").trim().toLowerCase(java.util.Locale.ROOT);
            if (!ProviderCatalog.exists(provider)) {
                throw new ai.indra.backend.http.ApiException(400, "bad_request", "Unknown AI provider: " + provider);
            }
            settings.setProvider(provider);
        }
        if (body.has("model")) {
            settings.setModel(Validation.optionalText("model", body.getString("model"), 120));
        }
        if (body.has("ttsVoice")) {
            settings.setTtsVoice(Validation.optionalText("ttsVoice", body.getString("ttsVoice"), 120));
        }
        if (body.has("speechRate")) {
            settings.setSpeechRate((float) body.getDouble("speechRate", 1.0d));
        }
        if (body.has("speechPitch")) {
            settings.setSpeechPitch((float) body.getDouble("speechPitch", 1.0d));
        }
        if (body.has("autoSpeak")) {
            settings.setAutoSpeak(body.getBoolean("autoSpeak", true));
        }
        if (body.has("memoryEnabled")) {
            settings.setMemoryEnabled(body.getBoolean("memoryEnabled", true));
        }
        if (body.has("autoMemory")) {
            settings.setAutoMemory(body.getBoolean("autoMemory", false));
        }
        if (body.has("wakeWordEnabled")) {
            settings.setWakeWordEnabled(body.getBoolean("wakeWordEnabled", false));
        }
        if (body.has("wakeWord")) {
            String wakeWord = Validation.optionalText("wakeWord", body.getString("wakeWord"), 40);
            settings.setWakeWord(wakeWord == null ? "indra" : wakeWord.toLowerCase(java.util.Locale.ROOT));
        }
        if (body.has("streamingEnabled")) {
            settings.setStreamingEnabled(body.getBoolean("streamingEnabled", true));
        }
        if (body.has("notificationsEnabled")) {
            settings.setNotificationsEnabled(body.getBoolean("notificationsEnabled", true));
        }
        if (body.has("syncEnabled")) {
            settings.setSyncEnabled(body.getBoolean("syncEnabled", true));
        }
        if (body.has("theme")) {
            settings.setTheme(Validation.requireOneOf("theme", body.getString("theme"),
                    java.util.Arrays.asList("system", "light", "dark")));
        }
        repository.saveSettings(settings);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("settings", settings.toJson());
        return response;
    }

    /** Catalogue data for the settings screen. */
    public JsonObject catalogue(User user) {
        RemoteConfig remote = repository.loadConfig();
        JsonArray personas = new JsonArray();
        for (Persona persona : Persona.values()) {
            personas.add(new JsonObject()
                    .put("id", persona.getId())
                    .put("label", persona.getLabel())
                    .put("description", persona.getDescription())
                    .put("enabled", Boolean.valueOf(remote.getAllowedPersonas().contains(persona.getId()))));
        }
        JsonArray brains = new JsonArray();
        for (BrainProfile brain : BrainProfile.values()) {
            brains.add(new JsonObject()
                    .put("id", brain.getId())
                    .put("label", brain.getLabel())
                    .put("description", brain.getDescription())
                    .put("enabled", Boolean.valueOf(remote.getAllowedBrains().contains(brain.getId()))));
        }
        JsonArray providers = new JsonArray();
        List<ProviderCatalog.Provider> all = ProviderCatalog.all();
        for (ProviderCatalog.Provider provider : all) {
            JsonObject json = new JsonObject();
            json.put("id", provider.getId());
            json.put("label", provider.getLabel());
            json.put("models", new JsonArray().addAll(provider.getModels()));
            json.put("requiresKey", Boolean.valueOf(provider.isRequiresKey()));
            json.put("supportsStreaming", Boolean.valueOf(provider.isSupportsStreaming()));
            json.put("defaultModel", provider.getDefaultModel());
            json.put("notes", provider.getNotes());
            json.put("enabled", Boolean.valueOf(remote.getEnabledProviders().contains(provider.getId())));
            providers.add(json);
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("personas", personas);
        response.put("brains", brains);
        response.put("providers", providers);
        response.put("languages", new JsonArray()
                .append(new JsonObject().put("code", "en").put("label", "English"))
                .append(new JsonObject().put("code", "ne").put("label", "नेपाली")));
        response.put("remote", remote.toJson());
        return response;
    }
}
