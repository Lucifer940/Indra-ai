package ai.indra.backend.service;

import ai.indra.backend.store.Repository;
import ai.indra.backend.http.ApiException;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.Device;
import ai.indra.core.model.Platform;
import ai.indra.core.model.User;
import ai.indra.core.security.Crypto;
import ai.indra.core.security.Validation;
import ai.indra.core.sync.PairingCodes;
import ai.indra.core.util.Ids;
import ai.indra.core.util.Times;

import java.util.List;

/**
 * Phone ↔ PC pairing plus device listing and revocation.
 *
 * <p>Pairing flow: an authenticated device calls {@code /devices/pair/start} and
 * shows the returned 8-character code (or QR). The other device sends the code to
 * {@code /devices/pair/complete} and receives its own session. Codes expire after
 * 10 minutes and are stored only as a SHA-256 hash.</p>
 */
public final class DeviceService {

    private static final long PAIRING_TTL_MS = 10L * 60L * 1000L;

    private final Repository repository;
    private final AuthService authService;

    public DeviceService(Repository repository, AuthService authService) {
        this.repository = repository;
        this.authService = authService;
    }

    public JsonObject startPairing(User user, JsonObject body) {
        String deviceName = Validation.requireText("deviceName", body.getString("deviceName", "My device"), 1, 80);
        Platform platform = Platform.fromName(body.getString("platform", "ANDROID"));
        String code = PairingCodes.generate();
        String lookup = lookupKey(code);
        long now = System.currentTimeMillis();

        JsonObject pairing = new JsonObject();
        String id = Ids.uuid();
        pairing.put("id", id);
        pairing.put("userId", user.getId());
        pairing.put("lookupKey", lookup);
        pairing.put("deviceName", deviceName);
        pairing.put("platform", platform.name());
        pairing.put("createdAt", Long.valueOf(now));
        pairing.put("updatedAt", Long.valueOf(now));
        pairing.put("expiresAt", Long.valueOf(now + PAIRING_TTL_MS));
        pairing.put("status", "pending");
        repository.savePairing(pairing);
        repository.audit(user.getId(), "device.pair.start", deviceName + " (" + platform + ")", null);

        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("code", code);
        response.put("expiresAt", Long.valueOf(now + PAIRING_TTL_MS));
        response.put("expiresInSeconds", Integer.valueOf((int) (PAIRING_TTL_MS / 1000L)));
        response.put("instructions", "Enter this code on the other device within 10 minutes. "
                + "Only your own devices should ever see it.");
        return response;
    }

    public JsonObject completePairing(User user, JsonObject body) {
        String code = Validation.requireText("code", body.getString("code"), 6, 12);
        String deviceName = Validation.requireText("deviceName", body.getString("deviceName", "My device"), 1, 80);
        Platform platform = Platform.fromName(body.getString("platform", "ANDROID"));
        JsonObject pairing = repository.findPairing(lookupKey(code));
        if (pairing == null) {
            throw ApiException.notFound("That pairing code is not valid. Generate a new one on your other device.");
        }
        if (pairing.getLong("expiresAt", 0L) < System.currentTimeMillis()) {
            repository.deletePairing(pairing.getString("id"));
            throw new ApiException(410, "pairing_expired", "That pairing code has expired. Please generate a new one.");
        }
        String pairingUserId = pairing.getString("userId");
        if (!pairingUserId.equals(user.getId())) {
            throw ApiException.forbidden("This pairing code belongs to a different account");
        }
        Device device = Device.create(user.getId(), deviceName, platform, null);
        device.setAppVersion(body.getString("appVersion"));
        repository.saveDevice(device);
        pairing.put("status", "used");
        pairing.put("usedAt", Long.valueOf(System.currentTimeMillis()));
        repository.savePairing(pairing);
        repository.audit(user.getId(), "device.pair.complete", deviceName + " (" + platform + ")", null);

        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("device", device.toJson());
        response.put("message", "Device paired. Memories and settings will now sync between your devices.");
        JsonObject session = authService.federatedSession(user);
        response.put("accessToken", session.getString("accessToken"));
        response.put("refreshToken", session.getString("refreshToken"));
        return response;
    }

    public JsonObject list(User user) {
        List<Device> devices = repository.listDevices(user.getId());
        JsonArray array = new JsonArray();
        long now = System.currentTimeMillis();
        for (Device device : devices) {
            JsonObject json = device.toJson();
            json.put("lastSeenIso", Times.toIso(device.getLastSeenAt()));
            json.put("online", Boolean.valueOf(now - device.getLastSeenAt() < 5L * 60L * 1000L));
            array.add(json);
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("devices", array);
        return response;
    }

    public JsonObject heartbeat(User user, JsonObject body) {
        String deviceId = body.getString("deviceId");
        Device device = repository.findDevice(user.getId(), deviceId);
        if (device == null) {
            throw ApiException.notFound("Unknown device - pair it again");
        }
        device.setLastSeenAt(System.currentTimeMillis());
        repository.saveDevice(device);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("serverTime", Long.valueOf(System.currentTimeMillis()));
        return response;
    }

    public JsonObject revoke(User user, String deviceId) {
        Device device = repository.findDevice(user.getId(), deviceId);
        if (device == null) {
            throw ApiException.notFound("Device not found");
        }
        device.setRevoked(true);
        repository.saveDevice(device);
        repository.deleteDevice(user.getId(), deviceId);
        repository.audit(user.getId(), "device.revoke", device.getName(), null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "Device removed. It can no longer sync until it is paired again.");
        return response;
    }

    private String lookupKey(String code) {
        return "pair:" + Crypto.sha256Hex("indra-pair-v1:" + PairingCodes.normalize(code));
    }
}
