package ai.indra.backend.service;

import ai.indra.backend.http.ApiException;
import ai.indra.backend.store.Repository;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.license.LicenseKeys;
import ai.indra.core.model.LicenseRecord;
import ai.indra.core.model.User;
import ai.indra.core.security.Validation;
import ai.indra.core.util.Times;

import java.util.List;

/**
 * Licence keys issued by the administrator. A key is validated on the server, so
 * a modified client cannot grant itself access.
 */
public final class LicenseService {

    private final Repository repository;

    public LicenseService(Repository repository) {
        this.repository = repository;
    }

    public JsonObject issue(User admin, JsonObject body) {
        int count = Math.max(1, Math.min(50, body.getInt("count", 1)));
        String plan = Validation.requireOneOf("plan", body.getString("plan", "personal"),
                java.util.Arrays.asList("personal", "family", "pro"));
        String email = body.getString("email");
        int expiresInDays = body.getInt("expiresInDays", 0);
        int maxDevices = Math.max(1, Math.min(10, body.getInt("maxDevices", 3)));
        long now = System.currentTimeMillis();

        JsonArray keys = new JsonArray();
        for (int i = 0; i < count; i++) {
            LicenseRecord record = new LicenseRecord();
            record.setKey(LicenseKeys.generate());
            record.setPlan(plan);
            record.setIssuedBy(admin.getId());
            record.setIssuedAt(now);
            record.setExpiresAt(expiresInDays > 0 ? now + expiresInDays * 24L * 60L * 60L * 1000L : 0L);
            record.setBoundEmail(email == null ? null : Validation.normalizeEmail(email));
            record.setMaxDevices(maxDevices);
            record.setNotes(Validation.optionalText("notes", body.getString("notes"), 300));
            repository.saveLicense(record);
            keys.add(record.toJson());
        }
        repository.audit(admin.getId(), "license.issue", count + " x " + plan, null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("keys", keys);
        response.put("message", "Share each key with one person. Keep a copy - the list is also under Admin → Licences.");
        return response;
    }

    public JsonObject list(User admin) {
        List<LicenseRecord> records = repository.listLicenses();
        JsonArray array = new JsonArray();
        for (LicenseRecord record : records) {
            JsonObject json = record.toJson();
            json.put("maskedKey", LicenseKeys.mask(record.getKey()));
            json.put("usable", Boolean.valueOf(record.isUsable(System.currentTimeMillis())));
            User bound = record.getBoundUserId() == null ? null : repository.findUser(record.getBoundUserId());
            json.put("boundEmail", bound == null ? record.getBoundEmail() : bound.getEmail());
            array.add(json);
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("licenses", array);
        response.put("count", Integer.valueOf(records.size()));
        return response;
    }

    public JsonObject revoke(User admin, String key) {
        LicenseRecord record = repository.findLicense(LicenseKeys.normalize(key));
        if (record == null) {
            throw ApiException.notFound("Licence not found");
        }
        record.setRevoked(true);
        repository.saveLicense(record);
        if (record.getBoundUserId() != null) {
            User bound = repository.findUser(record.getBoundUserId());
            if (bound != null && LicenseKeys.normalize(bound.getLicenseKey()).equals(LicenseKeys.normalize(key))) {
                bound.setLicenseKey(null);
                repository.saveUser(bound);
            }
        }
        repository.audit(admin.getId(), "license.revoke", LicenseKeys.mask(record.getKey()), null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "Licence revoked");
        return response;
    }

    /** Activates a key for the signed-in user and remembers it on the account. */
    public JsonObject activate(User user, JsonObject body) {
        String raw = Validation.requireText("key", body.getString("key"), 10, 40);
        String normalized = LicenseKeys.normalize(raw);
        if (!LicenseKeys.isValidFormat(normalized)) {
            throw ApiException.badRequest("That licence key is not valid. Check for typing mistakes.");
        }
        LicenseRecord record = repository.findLicense(normalized);
        if (record == null) {
            throw ApiException.notFound("This licence key was not issued by INDRA AI");
        }
        if (record.isRevoked()) {
            throw ApiException.forbidden("This licence has been revoked");
        }
        if (!record.isUsable(System.currentTimeMillis())) {
            throw new ApiException(410, "license_expired", "This licence has expired");
        }
        if (record.getBoundUserId() != null && !record.getBoundUserId().equals(user.getId())) {
            throw ApiException.conflict("This licence is already activated on another account");
        }
        record.setBoundUserId(user.getId());
        record.setBoundEmail(user.getEmail());
        repository.saveLicense(record);
        user.setLicenseKey(normalized);
        repository.saveUser(user);
        repository.audit(user.getId(), "license.activate", LicenseKeys.mask(normalized), null);

        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "Licence activated. This device and your account are unlocked.");
        response.put("plan", record.getPlan());
        response.put("expiresAt", Long.valueOf(record.getExpiresAt()));
        response.put("expiresIso", record.getExpiresAt() > 0L ? Times.toIso(record.getExpiresAt()) : "never");
        response.put("licenseKey", normalized);
        return response;
    }

    public boolean hasActiveLicense(User user) {
        if (user.getLicenseKey() == null) {
            return false;
        }
        LicenseRecord record = repository.findLicense(LicenseKeys.normalize(user.getLicenseKey()));
        return record != null && record.isUsable(System.currentTimeMillis());
    }
}
