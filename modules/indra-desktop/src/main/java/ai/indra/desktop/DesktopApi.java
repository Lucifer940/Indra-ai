package ai.indra.desktop;

import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTP client for the desktop app. Thin on purpose: every rule about
 * authorisation, rate limiting and validation lives on the server.
 */
public final class DesktopApi {

    /** API failure with a human-readable message. */
    public static final class ApiException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    /** Streaming callbacks. */
    public interface StreamListener {
        void onMeta(JsonObject meta);

        void onToken(String delta);

        void onDone(JsonObject result);

        void onError(String message);
    }

    private static final int CONNECT_TIMEOUT = 12000;
    private static final int READ_TIMEOUT = 90000;

    private final DesktopConfig config;
    private String accessToken;

    public DesktopApi(DesktopConfig config) {
        this.config = config;
        this.accessToken = config.getAccessToken();
    }

    public void setAccessToken(String token) {
        this.accessToken = token;
        config.setSession(token, config.getRefreshToken());
    }

    public String getAccessToken() {
        return accessToken;
    }

    private HttpURLConnection open(String method, String path) throws IOException {
        URL url = new URL(config.getBaseUrl() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("Accept", "application/json");
        if (accessToken != null && !accessToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        }
        return connection;
    }

    private JsonObject parseResponse(HttpURLConnection connection) throws ApiException {
        try {
            int status = connection.getResponseCode();
            String body = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status >= 300) {
                String message = "Request failed (" + status + ")";
                String code = "";
                try {
                    JsonObject error = JsonObject.parseOrEmpty(body).getObjectOrEmpty("error");
                    if (error.has("message")) {
                        message = error.getString("message");
                    }
                    code = error.getString("code", "");
                } catch (RuntimeException ignored) {
                    // keep generic message
                }
                throw new ApiException(status, code.isEmpty() ? message : code + ": " + message);
            }
            return body == null || body.trim().isEmpty() ? new JsonObject() : JsonObject.parse(body);
        } catch (IOException error) {
            throw new ApiException(0, "Network error: " + error.getMessage());
        } finally {
            connection.disconnect();
        }
    }

    private JsonObject call(String method, String path, JsonObject body) throws ApiException {
        HttpURLConnection connection = null;
        try {
            connection = open(method, path);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream out = connection.getOutputStream();
                out.write(body.toJson().getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();
            }
            return parseResponse(connection);
        } catch (IOException error) {
            if (connection != null) {
                connection.disconnect();
            }
            throw new ApiException(0, "Cannot reach " + config.getBaseUrl() + " - " + error.getMessage());
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            text.append(line).append('\n');
        }
        reader.close();
        return text.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException error) {
            return "";
        }
    }

    // -------------------------------------------------------------- health/auth

    public JsonObject health() throws ApiException {
        return call("GET", "/api/v1/health", null);
    }

    public JsonObject login(String email, String password) throws ApiException {
        JsonObject response = call("POST", "/api/v1/auth/login",
                new JsonObject().put("email", email).put("password", password));
        storeSession(response);
        return response;
    }

    public JsonObject register(String email, String password, String displayName) throws ApiException {
        JsonObject response = call("POST", "/api/v1/auth/register",
                new JsonObject().put("email", email).put("password", password)
                        .put("displayName", displayName));
        storeSession(response);
        return response;
    }

    public JsonObject oauthExchange(String provider, String code, String redirectUri, String verifier)
            throws ApiException {
        JsonObject response = call("POST", "/api/v1/auth/oauth/" + encode(provider) + "/exchange",
                new JsonObject().put("code", code).put("redirectUri", redirectUri)
                        .put("codeVerifier", verifier));
        storeSession(response);
        return response;
    }

    public JsonObject oauthStart(String provider, String state, String redirectUri, String challenge)
            throws ApiException {
        return call("GET", "/api/v1/auth/oauth/" + encode(provider) + "/start?state=" + encode(state)
                + "&redirectUri=" + encode(redirectUri) + "&codeChallenge=" + encode(challenge), null);
    }

    private void storeSession(JsonObject response) {
        String token = response.getString("accessToken", "");
        String refresh = response.getString("refreshToken", "");
        accessToken = token;
        config.setSession(token, refresh);
    }

    public JsonObject me() throws ApiException {
        return call("GET", "/api/v1/auth/me", null);
    }

    public void logout() {
        try {
            call("POST", "/api/v1/auth/logout", new JsonObject());
        } catch (ApiException error) {
            Log.warn("DesktopApi", "Logout call failed: " + error.getMessage());
        }
        accessToken = null;
        config.clearSession();
    }

    public JsonObject config() throws ApiException {
        return call("GET", "/api/v1/config", null);
    }

    public JsonObject activateLicense(String key) throws ApiException {
        return call("POST", "/api/v1/license/activate", new JsonObject().put("key", key));
    }

    // ------------------------------------------------------------------- chat

    public JsonObject chat(String message, String conversationId, String timeZone, boolean voiceMode,
                           String persona, String brain, String language) throws ApiException {
        JsonObject body = chatBody(message, conversationId, timeZone, voiceMode, persona, brain, language);
        return call("POST", "/api/v1/chat", body);
    }

    private JsonObject chatBody(String message, String conversationId, String timeZone, boolean voiceMode,
                                String persona, String brain, String language) {
        JsonObject body = new JsonObject()
                .put("message", message)
                .put("timeZone", timeZone)
                .put("voiceMode", Boolean.valueOf(voiceMode))
                .put("personaId", persona)
                .put("brainId", brain)
                .put("languageCode", language);
        if (conversationId != null && !conversationId.isEmpty()) {
            body.put("conversationId", conversationId);
        }
        return body;
    }

    /** Blocking streaming call - run it on a background thread. */
    public void stream(String message, String conversationId, String timeZone, boolean voiceMode,
                       String persona, String brain, String language, StreamListener listener) {
        HttpURLConnection connection = null;
        try {
            connection = open("POST", "/api/v1/chat/stream");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            OutputStream out = connection.getOutputStream();
            out.write(chatBody(message, conversationId, timeZone, voiceMode, persona, brain, language)
                    .toJson().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String error = readAll(connection.getErrorStream());
                listener.onError(error == null || error.isEmpty()
                        ? "Streaming failed (" + status + ")" : "Streaming failed (" + status + ")");
                return;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String event = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                } else if (line.isEmpty()) {
                    if (event != null && data.length() > 0) {
                        dispatch(event, data.toString(), listener);
                    }
                    event = null;
                    data.setLength(0);
                }
            }
            if (event != null && data.length() > 0) {
                dispatch(event, data.toString(), listener);
            }
            reader.close();
        } catch (IOException error) {
            listener.onError("Connection lost while streaming: " + error.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void dispatch(String event, String data, StreamListener listener) {
        JsonObject payload;
        try {
            payload = JsonObject.parseOrEmpty(data);
        } catch (RuntimeException error) {
            return;
        }
        if ("token".equals(event)) {
            listener.onToken(payload.getString("delta", ""));
        } else if ("meta".equals(event)) {
            listener.onMeta(payload);
        } else if ("done".equals(event)) {
            listener.onDone(payload);
        } else if ("error".equals(event)) {
            listener.onError(payload.getString("message", "The assistant could not answer"));
        }
    }

    // --------------------------------------------------- conversations/memory/tasks

    public JsonObject conversations() throws ApiException {
        return call("GET", "/api/v1/conversations", null);
    }

    public JsonObject messages(String conversationId) throws ApiException {
        return call("GET", "/api/v1/conversations/" + encode(conversationId) + "/messages", null);
    }

    public void deleteConversation(String id) throws ApiException {
        call("DELETE", "/api/v1/conversations/" + encode(id), null);
    }

    public JsonObject memories() throws ApiException {
        return call("GET", "/api/v1/memories", null);
    }

    public JsonObject createMemory(String content, String category, int importance) throws ApiException {
        return call("POST", "/api/v1/memories", new JsonObject().put("content", content)
                .put("category", category).put("importance", Integer.valueOf(importance)));
    }

    public JsonObject updateMemory(String id, JsonObject body) throws ApiException {
        return call("PUT", "/api/v1/memories/" + encode(id), body);
    }

    public void deleteMemory(String id) throws ApiException {
        call("DELETE", "/api/v1/memories/" + encode(id), null);
    }

    public JsonObject clearMemories() throws ApiException {
        return call("DELETE", "/api/v1/memories", null);
    }

    public JsonObject tasks(boolean includeCompleted) throws ApiException {
        return call("GET", "/api/v1/tasks?includeCompleted=" + includeCompleted, null);
    }

    public JsonObject createTask(JsonObject body) throws ApiException {
        return call("POST", "/api/v1/tasks", body);
    }

    public JsonObject updateTask(String id, JsonObject body) throws ApiException {
        return call("PUT", "/api/v1/tasks/" + encode(id), body);
    }

    public JsonObject completeTask(String id, String timeZone) throws ApiException {
        return call("POST", "/api/v1/tasks/" + encode(id) + "/complete",
                new JsonObject().put("timeZone", timeZone));
    }

    public void deleteTask(String id) throws ApiException {
        call("DELETE", "/api/v1/tasks/" + encode(id), null);
    }

    public JsonObject settings() throws ApiException {
        return call("GET", "/api/v1/settings", null);
    }

    public JsonObject saveSettings(JsonObject body) throws ApiException {
        return call("PUT", "/api/v1/settings", body);
    }

    public JsonObject settingsCatalogue() throws ApiException {
        return call("GET", "/api/v1/settings/catalogue", null);
    }

    public JsonObject keys() throws ApiException {
        return call("GET", "/api/v1/keys", null);
    }

    public JsonObject addKey(String provider, String label, String key, boolean permanent) throws ApiException {
        return call("POST", "/api/v1/keys", new JsonObject().put("provider", provider)
                .put("label", label).put("key", key).put("permanent", Boolean.valueOf(permanent)));
    }

    public void removeKey(String id) throws ApiException {
        call("DELETE", "/api/v1/keys/" + encode(id), null);
    }

    public JsonObject resetKeys(String provider) throws ApiException {
        return call("POST", "/api/v1/keys/reset",
                new JsonObject().put("provider", provider == null ? "" : provider));
    }

    public JsonObject devices() throws ApiException {
        return call("GET", "/api/v1/devices", null);
    }

    public JsonObject startPairing(String deviceName, String platform) throws ApiException {
        return call("POST", "/api/v1/devices/pair/start",
                new JsonObject().put("name", deviceName).put("platform", platform));
    }

    public JsonObject completePairing(String code, String deviceName, String platform) throws ApiException {
        return call("POST", "/api/v1/devices/pair/complete",
                new JsonObject().put("code", code).put("name", deviceName).put("platform", platform));
    }

    public void revokeDevice(String id) throws ApiException {
        call("DELETE", "/api/v1/devices/" + encode(id), null);
    }

    public JsonObject sync(JsonObject envelope) throws ApiException {
        return call("POST", "/api/v1/sync", envelope);
    }

    public JsonObject weather(String location) throws ApiException {
        return call("GET", "/api/v1/weather?location=" + encode(location == null ? "" : location), null);
    }

    public JsonObject search(String query, boolean news) throws ApiException {
        return call("GET", "/api/v1/search?q=" + encode(query) + "&news=" + news + "&limit=5", null);
    }

    public JsonObject adminUsers(String query) throws ApiException {
        return call("GET", "/api/v1/admin/users?q=" + encode(query == null ? "" : query), null);
    }
}
