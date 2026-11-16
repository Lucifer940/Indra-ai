package ai.indra.backend;

import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test against a real HTTP server on a random port with a temporary
 * data directory. No AI provider is configured, so chat must fall back to the
 * deterministic offline brain - proving the failover path works.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    private static ApiApplication application;
    private static String baseUrl;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static String accessToken;
    private static String adminToken;
    private static String conversationId;
    private static String memoryId;
    private static String taskId;
    private static String deviceId;

    @BeforeAll
    static void startServer() throws IOException {
        int port = freePort();
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("INDRA_PORT", String.valueOf(port));
        values.put("INDRA_BIND", "127.0.0.1");
        values.put("INDRA_DATA_DIR", Files.createTempDirectory("indra-test").toString());
        values.put("INDRA_JWT_SECRET", "test-secret-0123456789abcdef0123456789abcdef");
        values.put("INDRA_MASTER_KEY", "test-master-0123456789abcdef0123456789abcdef");
        values.put("INDRA_RATE_LIMIT", "1000");
        values.put("INDRA_AUTH_RATE_LIMIT", "1000");
        values.put("INDRA_DEBUG", "true");
        BackendConfig config = BackendConfig.fromMap(values);
        application = new ApiApplication(config);
        application.start();
        baseUrl = "http://127.0.0.1:" + port;
    }

    @AfterAll
    static void stopServer() {
        if (application != null) {
            application.stop();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpResponse<String> call(String method, String path, JsonObject body, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        String payload = body == null ? "" : body.toJson();
        if ("GET".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        }
        try {
            return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException("HTTP call failed: " + method + " " + path, error);
        }
    }

    private static JsonObject json(HttpResponse<String> response) {
        return JsonObject.parse(response.body());
    }

    @Test
    @Order(1)
    void healthAndConfigArePublic() {
        HttpResponse<String> health = call("GET", "/api/v1/health", null, null);
        assertEquals(200, health.statusCode());
        assertEquals("healthy", json(health).getString("status"));

        HttpResponse<String> config = call("GET", "/api/v1/config", null, null);
        assertEquals(200, config.statusCode());
        assertTrue(json(config).getObjectOrEmpty("config").has("minVersion"));

        assertEquals(404, call("GET", "/api/v1/does-not-exist", null, null).statusCode());
    }

    @Test
    @Order(2)
    void registrationAndLoginWork() {
        JsonObject body = new JsonObject()
                .put("email", "umesh@example.com")
                .put("password", "UmeshTharu998")
                .put("displayName", "Umesh");
        HttpResponse<String> register = call("POST", "/api/v1/auth/register", body, null);
        assertEquals(201, register.statusCode(), register.body());
        JsonObject registered = json(register);
        accessToken = registered.getString("accessToken");
        adminToken = accessToken;
        assertNotNull(accessToken);
        assertEquals("ADMIN", registered.getObjectOrEmpty("user").getString("role"),
                "first account becomes administrator");

        // Duplicate registration is rejected.
        assertEquals(409, call("POST", "/api/v1/auth/register", body, null).statusCode());

        // Weak password is rejected with a field error.
        HttpResponse<String> weak = call("POST", "/api/v1/auth/register",
                new JsonObject().put("email", "weak@example.com").put("password", "short"), null);
        assertEquals(400, weak.statusCode());
        assertEquals("validation_failed", json(weak).getObjectOrEmpty("error").getString("code"));

        HttpResponse<String> login = call("POST", "/api/v1/auth/login",
                new JsonObject().put("email", "umesh@example.com").put("password", "UmeshTharu998"), null);
        assertEquals(200, login.statusCode());
        accessToken = json(login).getString("accessToken");

        assertEquals(401, call("POST", "/api/v1/auth/login",
                new JsonObject().put("email", "umesh@example.com").put("password", "wrong-password"), null).statusCode());

        HttpResponse<String> me = call("GET", "/api/v1/auth/me", null, accessToken);
        assertEquals(200, me.statusCode());
        assertEquals("umesh@example.com", json(me).getObjectOrEmpty("user").getString("email"));

        assertEquals(401, call("GET", "/api/v1/auth/me", null, null).statusCode());
        assertEquals(401, call("GET", "/api/v1/auth/me", null, "not-a-token").statusCode());
    }

    @Test
    @Order(3)
    void chatFallsBackToOfflineBrainAndStoresHistory() {
        JsonObject body = new JsonObject()
                .put("message", "Explain photosynthesis in one sentence")
                .put("timeZone", "Asia/Kathmandu");
        HttpResponse<String> response = call("POST", "/api/v1/chat", body, accessToken);
        assertEquals(200, response.statusCode(), response.body());
        JsonObject result = json(response);
        assertTrue(result.getBoolean("offline", false), "no provider configured -> offline brain");
        assertTrue(result.getString("reply", "").toLowerCase().contains("offline"));
        conversationId = result.getString("conversationId");
        assertNotNull(conversationId);

        HttpResponse<String> messages = call("GET", "/api/v1/conversations/" + conversationId + "/messages",
                null, accessToken);
        assertEquals(200, messages.statusCode());
        assertEquals(2, json(messages).getArrayOrEmpty("messages").size());

        HttpResponse<String> list = call("GET", "/api/v1/conversations", null, accessToken);
        assertTrue(json(list).getArrayOrEmpty("conversations").size() >= 1);
    }

    @Test
    @Order(4)
    void memoryCommandsWorkEndToEnd() {
        HttpResponse<String> remembered = call("POST", "/api/v1/chat",
                new JsonObject().put("message", "Remember that my exam is on 12 December")
                        .put("timeZone", "Asia/Kathmandu"), accessToken);
        assertEquals(200, remembered.statusCode());
        assertTrue(json(remembered).getString("reply", "").toLowerCase().contains("remember"));

        HttpResponse<String> list = call("GET", "/api/v1/memories", null, accessToken);
        assertEquals(200, list.statusCode());
        JsonArray memories = json(list).getArrayOrEmpty("memories");
        assertEquals(1, memories.size());
        assertTrue(memories.getObject(0).getString("content", "").contains("12 December"));
        memoryId = memories.getObject(0).getString("id");

        HttpResponse<String> ask = call("POST", "/api/v1/chat",
                new JsonObject().put("message", "what do you remember about me").put("timeZone", "UTC"), accessToken);
        assertTrue(json(ask).getString("reply", "").contains("12 December"));

        HttpResponse<String> forget = call("POST", "/api/v1/chat",
                new JsonObject().put("message", "forget that my exam is on 12 December").put("timeZone", "UTC"), accessToken);
        assertEquals(200, forget.statusCode());
        assertEquals(0, json(call("GET", "/api/v1/memories", null, accessToken)).getArrayOrEmpty("memories").size());
    }

    @Test
    @Order(5)
    void tasksAndRemindersWork() {
        long when = System.currentTimeMillis() + 60L * 60L * 1000L;
        HttpResponse<String> created = call("POST", "/api/v1/tasks", new JsonObject()
                .put("title", "Attend meeting")
                .put("remindAt", Long.valueOf(when))
                .put("recurrence", "daily")
                .put("timeZone", "Asia/Kathmandu"), accessToken);
        assertEquals(201, created.statusCode(), created.body());
        taskId = json(created).getObjectOrEmpty("task").getString("id");
        assertNotNull(taskId);

        HttpResponse<String> list = call("GET", "/api/v1/tasks", null, accessToken);
        assertEquals(1, json(list).getArrayOrEmpty("tasks").size());

        HttpResponse<String> completed = call("POST", "/api/v1/tasks/" + taskId + "/complete",
                new JsonObject().put("timeZone", "Asia/Kathmandu"), accessToken);
        assertEquals(200, completed.statusCode());
        // A daily reminder rolls forward instead of completing.
        assertTrue(json(completed).getString("message", "").toLowerCase().contains("next"));

        assertEquals(400, call("POST", "/api/v1/tasks",
                new JsonObject().put("title", "Past reminder").put("remindAt", Long.valueOf(1000L)), accessToken).statusCode());
    }

    @Test
    @Order(6)
    void settingsAndCatalogueWork() {
        HttpResponse<String> updated = call("PUT", "/api/v1/settings", new JsonObject()
                .put("personaId", "professional")
                .put("brainId", "strategic")
                .put("languageCode", "ne")
                .put("speechRate", Double.valueOf(1.1d))
                .put("memoryEnabled", Boolean.TRUE), accessToken);
        assertEquals(200, updated.statusCode(), updated.body());
        JsonObject settings = json(updated).getObjectOrEmpty("settings");
        assertEquals("professional", settings.getString("personaId"));
        assertEquals("strategic", settings.getString("brainId"));
        assertEquals("ne", settings.getString("languageCode"));

        HttpResponse<String> catalogue = call("GET", "/api/v1/settings/catalogue", null, accessToken);
        assertEquals(200, catalogue.statusCode());
        JsonObject data = json(catalogue);
        assertEquals(7, data.getArrayOrEmpty("personas").size());
        assertEquals(6, data.getArrayOrEmpty("brains").size());
        assertTrue(data.getArrayOrEmpty("providers").size() >= 8);
    }

    @Test
    @Order(7)
    void apiKeysAreMaskedAndResettable() {
        HttpResponse<String> added = call("POST", "/api/v1/keys", new JsonObject()
                .put("provider", "openai")
                .put("label", "primary")
                .put("key", "sk-test-1234567890abcdef")
                .put("permanent", Boolean.TRUE), accessToken);
        assertEquals(201, added.statusCode(), added.body());
        assertFalse(added.body().contains("sk-test-1234567890abcdef"), "full key must never be returned");

        HttpResponse<String> duplicate = call("POST", "/api/v1/keys", new JsonObject()
                .put("provider", "openai")
                .put("key", "sk-test-1234567890abcdef"), accessToken);
        assertEquals(201, duplicate.statusCode());

        HttpResponse<String> list = call("GET", "/api/v1/keys", null, accessToken);
        assertEquals(1, json(list).getArrayOrEmpty("keys").size(), "identical key is not stored twice");
        assertTrue(json(list).getArrayOrEmpty("keys").getObject(0).getString("maskedKey", "").contains("..."));

        assertEquals(400, call("POST", "/api/v1/keys",
                new JsonObject().put("provider", "nope").put("key", "sk-1234567890"), accessToken).statusCode());

        HttpResponse<String> reset = call("POST", "/api/v1/keys/reset", new JsonObject(), accessToken);
        assertEquals(200, reset.statusCode());
        assertEquals(0, json(call("GET", "/api/v1/keys", null, accessToken)).getArrayOrEmpty("keys").size());
    }

    @Test
    @Order(8)
    void pairingAndSyncWork() {
        HttpResponse<String> start = call("POST", "/api/v1/devices/pair/start",
                new JsonObject().put("deviceName", "Umesh PC").put("platform", "WINDOWS"), accessToken);
        assertEquals(201, start.statusCode(), start.body());
        String code = json(start).getString("code");
        assertNotNull(code);

        HttpResponse<String> complete = call("POST", "/api/v1/devices/pair/complete",
                new JsonObject().put("code", code).put("deviceName", "Pixel 8").put("platform", "ANDROID"), accessToken);
        assertEquals(200, complete.statusCode(), complete.body());
        deviceId = json(complete).getObjectOrEmpty("device").getString("id");
        assertNotNull(deviceId);

        HttpResponse<String> devices = call("GET", "/api/v1/devices", null, accessToken);
        assertEquals(1, json(devices).getArrayOrEmpty("devices").size());

        JsonObject envelope = new JsonObject()
                .put("deviceId", deviceId)
                .put("cursor", Long.valueOf(0L))
                .put("memories", new JsonArray().append(new JsonObject()
                        .put("id", "11111111-1111-1111-1111-111111111111")
                        .put("content", "Loves momo")
                        .put("category", "PREFERENCE")
                        .put("importance", Integer.valueOf(4))
                        .put("updatedAt", Long.valueOf(System.currentTimeMillis()))));
        HttpResponse<String> sync = call("POST", "/api/v1/sync", envelope, accessToken);
        assertEquals(200, sync.statusCode(), sync.body());
        assertTrue(json(sync).getArrayOrEmpty("memories").size() >= 1);
        assertTrue(json(sync).getLong("cursor", 0L) > 0L);

        HttpResponse<String> revoked = call("DELETE", "/api/v1/devices/" + deviceId, null, accessToken);
        assertEquals(200, revoked.statusCode());
        assertEquals(0, json(call("GET", "/api/v1/devices", null, accessToken)).getArrayOrEmpty("devices").size());
    }

    @Test
    @Order(9)
    void licenceIssueAndActivationWork() {
        HttpResponse<String> issued = call("POST", "/api/v1/admin/licenses",
                new JsonObject().put("plan", "personal").put("count", Integer.valueOf(1)), adminToken);
        assertEquals(201, issued.statusCode(), issued.body());
        String key = json(issued).getArrayOrEmpty("keys").getObject(0).getString("key");
        assertNotNull(key);

        HttpResponse<String> activated = call("POST", "/api/v1/license/activate",
                new JsonObject().put("key", key), accessToken);
        assertEquals(200, activated.statusCode(), activated.body());
        assertEquals("personal", json(activated).getString("plan"));

        assertEquals(400, call("POST", "/api/v1/license/activate",
                new JsonObject().put("key", "INDRA-AAAA-BBBB-CCCC-DDDE"), accessToken).statusCode());
    }

    @Test
    @Order(10)
    void adminEndpointsRequireAdminRole() {
        HttpResponse<String> users = call("GET", "/api/v1/admin/users", null, adminToken);
        assertEquals(200, users.statusCode());
        assertTrue(json(users).getArrayOrEmpty("users").size() >= 1);

        HttpResponse<String> config = call("PUT", "/api/v1/admin/config", new JsonObject()
                .put("announcement", "Welcome to INDRA AI 1.1")
                .put("googleLoginEnabled", Boolean.TRUE), adminToken);
        assertEquals(200, config.statusCode());
        int revision = json(config).getObjectOrEmpty("config").getInt("revision", 0);
        assertTrue(revision >= 2);

        HttpResponse<String> publicConfig = call("GET", "/api/v1/config", null, null);
        assertEquals("Welcome to INDRA AI 1.1",
                json(publicConfig).getObjectOrEmpty("config").getString("announcement"));

        // A normal user must be rejected.
        HttpResponse<String> second = call("POST", "/api/v1/auth/register", new JsonObject()
                .put("email", "normal@example.com").put("password", "NormalUser123"), null);
        assertEquals(201, second.statusCode());
        String userToken = json(second).getString("accessToken");
        assertEquals(403, call("GET", "/api/v1/admin/users", null, userToken).statusCode());

        HttpResponse<String> audit = call("GET", "/api/v1/admin/audit", null, adminToken);
        assertEquals(200, audit.statusCode());
        assertTrue(json(audit).getArrayOrEmpty("events").size() > 0);

        HttpResponse<String> stats = call("GET", "/api/v1/admin/stats", null, adminToken);
        assertEquals(200, stats.statusCode());
        assertTrue(json(stats).getObjectOrEmpty("counts").getInt("users", 0) >= 2);
    }

    @Test
    @Order(11)
    void logoutRevokesSessions() {
        HttpResponse<String> logout = call("POST", "/api/v1/auth/logout", new JsonObject(), accessToken);
        assertEquals(200, logout.statusCode());
        assertEquals(401, call("GET", "/api/v1/auth/me", null, accessToken).statusCode());
        // Admin session (separate token version bump) is also invalidated for that user.
        assertEquals(401, call("GET", "/api/v1/admin/users", null, adminToken).statusCode());
    }
}
