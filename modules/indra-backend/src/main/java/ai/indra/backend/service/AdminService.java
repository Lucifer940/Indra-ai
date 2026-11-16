package ai.indra.backend.service;

import ai.indra.backend.BackendConfig;
import ai.indra.backend.http.ApiException;
import ai.indra.backend.store.Repository;
import ai.indra.core.config.RemoteConfig;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.Role;
import ai.indra.core.model.User;
import ai.indra.core.util.Times;

import java.util.List;

/** Administrator-only operations: users, app configuration, health and logs. */
public final class AdminService {

    private final Repository repository;
    private final BackendConfig config;
    private final long startedAt = System.currentTimeMillis();

    public AdminService(Repository repository, BackendConfig config) {
        this.repository = repository;
        this.config = config;
    }

    public JsonObject users(int limit, int offset, String query) {
        List<User> users = repository.listUsers(limit, offset);
        JsonArray array = new JsonArray();
        String needle = query == null ? null : query.trim().toLowerCase(java.util.Locale.ROOT);
        for (User user : users) {
            if (needle != null && !needle.isEmpty()
                    && !user.getEmail().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    && !user.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                continue;
            }
            JsonObject json = user.toJson();
            json.put("lastLoginIso", user.getLastLoginAt() > 0L ? Times.toIso(user.getLastLoginAt()) : "never");
            json.put("memoryCount", Integer.valueOf(repository.listMemories(user.getId(), false).size()));
            array.add(json);
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("users", array);
        response.put("total", Integer.valueOf(repository.countUsers()));
        return response;
    }

    public JsonObject updateUser(User admin, String userId, JsonObject body) {
        User target = repository.findUser(userId);
        if (target == null) {
            throw ApiException.notFound("User not found");
        }
        if (body.has("role")) {
            Role role = Role.fromName(body.getString("role"));
            if (target.getId().equals(admin.getId()) && role != Role.ADMIN) {
                throw ApiException.badRequest("You cannot remove your own administrator role");
            }
            target.setRole(role);
        }
        if (body.has("disabled")) {
            boolean disabled = body.getBoolean("disabled", false);
            if (target.getId().equals(admin.getId()) && disabled) {
                throw ApiException.badRequest("You cannot disable your own account");
            }
            target.setDisabled(disabled);
        }
        if (body.has("displayName")) {
            target.setDisplayName(ai.indra.core.security.Validation.requireText("displayName",
                    body.getString("displayName"), 1, 60));
        }
        repository.saveUser(target);
        repository.audit(admin.getId(), "admin.user.update", target.getEmail() + " role=" + target.getRole()
                + " disabled=" + target.isDisabled(), null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("user", target.toJson());
        return response;
    }

    public JsonObject deleteUser(User admin, String userId) {
        User target = repository.findUser(userId);
        if (target == null) {
            throw ApiException.notFound("User not found");
        }
        if (target.getId().equals(admin.getId())) {
            throw ApiException.badRequest("Delete your own account from Settings → Account instead");
        }
        repository.audit(admin.getId(), "admin.user.delete", target.getEmail(), null);
        repository.getStore().deleteUserData(userId);
        repository.getStore().delete(Repository.USERS, userId);
        repository.setMeta("tokenVersion:" + userId, "deleted");
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "User deleted");
        return response;
    }

    public JsonObject getConfig() {
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("config", repository.loadConfig().toJson());
        return response;
    }

    /** Publishes new remote configuration; every client picks it up on next poll. */
    public JsonObject updateConfig(User admin, JsonObject body) {
        RemoteConfig current = repository.loadConfig();
        JsonObject merged = current.toJson();
        for (String key : body.keySet()) {
            if (!"revision".equals(key) && !"updatedAt".equals(key)) {
                merged.put(key, body.get(key));
            }
        }
        RemoteConfig updated = RemoteConfig.fromJson(merged);
        updated.setRevision(current.getRevision() + 1L);
        repository.saveConfig(updated);
        repository.audit(admin.getId(), "admin.config.update", "revision " + updated.getRevision(), null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("config", updated.toJson());
        response.put("message", "Configuration published to all clients (revision " + updated.getRevision() + ")");
        return response;
    }

    public JsonObject audit(int limit) {
        JsonArray entries = new JsonArray();
        for (JsonObject entry : repository.recentAudit(Math.min(limit, 500))) {
            entries.add(entry);
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("events", entries);
        return response;
    }

    public JsonObject health() {
        long now = System.currentTimeMillis();
        JsonObject storage = new JsonObject();
        storage.put("engine", repository.getStore().describe());
        storage.put("users", Integer.valueOf(repository.countUsers()));
        JsonObject providers = new JsonObject();
        for (String providerId : ai.indra.core.ai.ProviderCatalog.ids()) {
            providers.put(providerId, Boolean.valueOf(config.providerKey(providerId) != null));
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("status", "healthy");
        response.put("version", "1.1.0");
        response.put("uptimeSeconds", Long.valueOf((now - startedAt) / 1000L));
        response.put("serverTime", Long.valueOf(now));
        response.put("storage", storage);
        response.put("serverProvidersConfigured", providers);
        response.put("registrationOpen", Boolean.valueOf(repository.loadConfig().isRegistrationOpen()));
        return response;
    }

    public JsonObject stats() {
        JsonObject counts = new JsonObject();
        counts.put("users", Integer.valueOf(repository.countUsers()));
        counts.put("licenses", Integer.valueOf(repository.listLicenses().size()));
        counts.put("memories", Integer.valueOf(repository.getStore().count(Repository.MEMORIES)));
        counts.put("tasks", Integer.valueOf(repository.getStore().count(Repository.TASKS)));
        counts.put("conversations", Integer.valueOf(repository.getStore().count(Repository.CONVERSATIONS)));
        counts.put("messages", Integer.valueOf(repository.getStore().count(Repository.MESSAGES)));
        counts.put("devices", Integer.valueOf(repository.getStore().count(Repository.DEVICES)));
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("counts", counts);
        response.put("configRevision", Long.valueOf(repository.loadConfig().getRevision()));
        return response;
    }
}
