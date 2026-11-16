package ai.indra.backend;

import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the configuration the Android app uses: the backend built for a local
 * device, bound to 127.0.0.1 on an operating-system assigned port, with the
 * device-session endpoint enabled.
 *
 * <p>This is the same {@link ApiApplication} the phone starts, so these tests cover
 * the embedded engine's startup, its plain-Java HTTP server (keep-alive and chunked
 * Server-Sent Events), the device session, and data surviving a restart.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalModeIntegrationTest {

    private static final String DEVICE_ID = "itest-device-0123456789abcdef";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static Path dataDir;
    private static ApiApplication application;
    private static String baseUrl;
    private static String accessToken;
    private static String userId;

    private static void startEngine() throws IOException {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("INDRA_DATA_DIR", dataDir.toString());
        values.put("INDRA_BIND", "127.0.0.1");
        values.put("INDRA_PORT", "0"); // let the OS pick, exactly like the app
        values.put("INDRA_LOCAL_MODE", "true");
        values.put("INDRA_JWT_SECRET", "local-test-jwt-0123456789abcdef0123456789");
        values.put("INDRA_MASTER_KEY", "local-test-master-0123456789abcdef012345");
        values.put("INDRA_RATE_LIMIT", "5000");
        values.put("INDRA_AUTH_RATE_LIMIT", "500");
        BackendConfig config = BackendConfig.fromMap(values);
        application = new ApiApplication(config);
        application.start();
        assertTrue(application.getPort() > 0, "the engine must report the port it bound");
        baseUrl = "http://127.0.0.1:" + application.getPort();
    }

    private static void stopEngine() {
        if (application != null) {
            application.stop();
            application = null;
        }
    }

    private static HttpResponse<String> call(String method, String path, JsonObject body, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("GET".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            String payload = body == null ? "" : body.toJson();
            builder.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    @Order(1)
    void engineStartsOnLoopbackWithAFreePort() throws Exception {
        dataDir = Files.createTempDirectory("indra-local-engine");
        startEngine();

        HttpResponse<String> health = call("GET", "/api/v1/health", null, null);
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"ok\""), health.body());
        assertTrue(baseUrl.startsWith("http://127.0.0.1:"), baseUrl);
    }

    @Test
    @Order(2)
    void deviceSessionNeedsNoAccountAndIsStable() throws Exception {
        JsonObject deviceMissing = new JsonObject();
        assertEquals(400, call("POST", "/api/v1/auth/local", deviceMissing, null).statusCode(),
                "a device id is required");

        JsonObject body = new JsonObject().put("deviceId", DEVICE_ID);
        HttpResponse<String> first = call("POST", "/api/v1/auth/local", body, null);
        assertEquals(200, first.statusCode(), first.body());
        JsonObject session = JsonObject.parse(first.body());
        accessToken = session.getString("accessToken", "");
        assertFalse(accessToken.isEmpty(), "the device session must include an access token");
        JsonObject user = session.getObjectOrEmpty("user");
        userId = user.getString("id", "");
        assertTrue(user.getString("email", "").endsWith("@local.indra"), user.getString("email", ""));

        HttpResponse<String> second = call("POST", "/api/v1/auth/local", body, null);
        assertEquals(userId, JsonObject.parse(second.body()).getObjectOrEmpty("user").getString("id", ""),
                "the same installation must map to the same account");

        HttpResponse<String> me = call("GET", "/api/v1/auth/me", null, accessToken);
        assertEquals(200, me.statusCode(), me.body());
    }

    @Test
    @Order(3)
    void streamingChatUsesServerSentEvents() throws Exception {
        JsonObject body = new JsonObject()
                .put("message", "hello there")
                .put("timeZone", "Asia/Kathmandu");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/chat/stream"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJson(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), response.body());
        String stream = response.body();
        assertTrue(stream.contains("event: token"), stream);
        assertTrue(stream.contains("event: done"), stream);
        assertFalse(stream.contains("event: error"), stream);
    }

    @Test
    @Order(4)
    void memoriesAndTasksSurviveARestart() throws Exception {
        JsonObject memory = new JsonObject()
                .put("content", "Prefers tea over coffee")
                .put("category", "PREFERENCE");
        assertEquals(201, call("POST", "/api/v1/memories", memory, accessToken).statusCode());

        JsonObject task = new JsonObject()
                .put("title", "Call the plumber")
                .put("dueAt", Long.valueOf(System.currentTimeMillis() + 3_600_000L));
        assertEquals(201, call("POST", "/api/v1/tasks", task, accessToken).statusCode());

        // Restart exactly like the phone does when its process is recreated.
        stopEngine();
        startEngine();

        JsonObject device = new JsonObject().put("deviceId", DEVICE_ID);
        HttpResponse<String> relogin = call("POST", "/api/v1/auth/local", device, null);
        assertEquals(200, relogin.statusCode(), relogin.body());
        accessToken = JsonObject.parse(relogin.body()).getString("accessToken", "");

        JsonObject memories = JsonObject.parse(call("GET", "/api/v1/memories", null, accessToken).body());
        JsonArray list = memories.getArrayOrEmpty("memories");
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.getObject(i).getString("content", "").contains("tea")) {
                found = true;
            }
        }
        assertTrue(found, "the memory must be there after a restart: " + memories.toJson());

        JsonObject tasks = JsonObject.parse(call("GET", "/api/v1/tasks", null, accessToken).body());
        assertTrue(tasks.toJson().contains("Call the plumber"), tasks.toJson());
    }

    @Test
    @Order(5)
    void localEndpointIsAbsentWhenNotInLocalMode() throws Exception {
        stopEngine();
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("INDRA_DATA_DIR", dataDir.toString());
        values.put("INDRA_BIND", "127.0.0.1");
        values.put("INDRA_PORT", "0");
        values.put("INDRA_JWT_SECRET", "local-test-jwt-0123456789abcdef0123456789");
        values.put("INDRA_MASTER_KEY", "local-test-master-0123456789abcdef012345");
        ApiApplication normal = new ApiApplication(BackendConfig.fromMap(values));
        normal.start();
        try {
            String url = "http://127.0.0.1:" + normal.getPort() + "/api/v1/auth/local";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"deviceId\":\"abcdefgh12345678\"}"))
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode(),
                    "a normal server deployment must not expose the device session route");
        } finally {
            normal.stop();
        }
    }

    @AfterAll
    static void tearDown() {
        stopEngine();
    }
}
