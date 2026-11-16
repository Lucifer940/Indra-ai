package ai.indra.backend.service;

import ai.indra.backend.http.ApiException;
import ai.indra.core.ai.ProviderCatalog;
import ai.indra.core.apikeys.ApiKeyVault;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.ApiKeyEntry;
import ai.indra.core.model.User;
import ai.indra.core.security.Validation;
import ai.indra.core.util.Strings;

/** Settings → AI Providers: multiple permanent keys per provider plus reset. */
public final class KeyService {

    private final ApiKeyVault vault;

    public KeyService(ApiKeyVault vault) {
        this.vault = vault;
    }

    public JsonObject list(User user) {
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("keys", vault.describe(user.getId()));
        JsonArray providers = new JsonArray();
        for (ProviderCatalog.Provider provider : ProviderCatalog.keyProviders()) {
            providers.add(new JsonObject()
                    .put("id", provider.getId())
                    .put("label", provider.getLabel())
                    .put("models", new JsonArray().addAll(provider.getModels()))
                    .put("notes", provider.getNotes()));
        }
        response.put("providers", providers);
        return response;
    }

    public JsonObject add(User user, JsonObject body) {
        String provider = Validation.requireText("provider", body.getString("provider"), 2, 40);
        String key = Validation.requireText("key", body.getString("key"), 8, 400);
        String label = Validation.optionalText("label", body.getString("label"), 80);
        try {
            ApiKeyEntry entry = vault.addKey(user.getId(), provider, label, key.trim(),
                    body.getBoolean("permanent", true));
            audit(user, "apikey.add", provider + " / " + entry.getMaskedKey());
            JsonObject response = new JsonObject();
            response.put("ok", Boolean.TRUE);
            response.put("message", "Key saved. It is stored encrypted and never shown again in full.");
            response.put("key", entry.toJson());
            return response;
        } catch (IllegalArgumentException error) {
            throw ApiException.badRequest(error.getMessage());
        }
    }

    public JsonObject remove(User user, String id) {
        if (!vault.removeKey(user.getId(), id)) {
            throw ApiException.notFound("Key not found");
        }
        audit(user, "apikey.remove", id);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "Key deleted");
        return response;
    }

    public JsonObject toggle(User user, String id, JsonObject body) {
        vault.setActive(user.getId(), id, body.getBoolean("active", true));
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "Key updated");
        return response;
    }

    /** "Reset all keys" - permanently forgets every stored key. */
    public JsonObject reset(User user, JsonObject body) {
        String provider = body.getString("provider");
        int removed = Strings.isBlank(provider)
                ? vault.resetAll(user.getId())
                : vault.resetProvider(user.getId(), provider.trim().toLowerCase(java.util.Locale.ROOT));
        audit(user, "apikey.reset", Strings.isBlank(provider) ? "all providers" : provider);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", removed == 0 ? "There were no keys to reset."
                : removed + " key" + (removed == 1 ? "" : "s") + " removed");
        response.put("removed", Integer.valueOf(removed));
        return response;
    }

    private void audit(User user, String action, String detail) {
        if (auditSink != null) {
            auditSink.record(user, action, detail);
        }
    }

    /** Wired by {@link Services} so key changes land in the audit log. */
    public interface AuditSink {
        void record(User user, String action, String detail);
    }

    private AuditSink auditSink;

    public void setAuditSink(AuditSink sink) {
        this.auditSink = sink;
    }

}
