package ai.indra.android.data;

import android.content.Context;

import ai.indra.android.local.LocalBackend;
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
 * HTTP client for the INDRA backend.
 *
 * <p>The client is deliberately thin: authorisation, rate limiting, memory rules and
 * provider access all live on the server. Access tokens are refreshed once on a 401
 * and stored encrypted by {@link Prefs}. Streaming uses the server's SSE endpoint so
 * replies appear as they are generated.</p>
 */
public final class ApiClient {

    /** Failure with a user-presentable message and HTTP status (0 = network). */
    public static final class ApiException extends Exception {
        private static final long serialVersionUID = 1L;

        private final int status;
        private final String code;
        private final String field;

        public ApiException(int status, String code, String message, String field) {
            super(message);
            this.status = status;
            this.code = code == null ? "ERROR" : code;
            this.field = field;
        }

        public int getStatus() {
            return status;
        }

        public String getCode() {
            return code;
        }

        public String getField() {
            return field;
        }

        public boolean isUnauthorized() {
            return status == 401;
        }

        public boolean isOffline() {
            return status == 0;
        }
    }

    /** Streaming callbacks, delivered on the calling background thread. */
    public interface StreamListener {
        void onMeta(JsonObject meta);

        void onToken(String delta);

        void onDone(JsonObject result);

        void onError(String message);
    }

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 120000;

    private final Context context;
    private final Prefs prefs;
    private final Object refreshLock = new Object();

    public ApiClient(Context context, Prefs prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
    }

    /**
     * Points the app at an explicit server (a PC or a hosted deployment).
     * A loopback address switches back to the engine embedded in this phone.
     */
    public void setBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        String cleaned = url.trim();
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        boolean loopback = cleaned.contains("127.0.0.1") || cleaned.contains("localhost");
        prefs.setUseEmbeddedBackend(loopback);
        if (!loopback) {
            prefs.setBaseUrl(cleaned);
        }
    }

    /** Address of the on-device engine, or the configured remote server. */
    public String baseUrl() {
        if (prefs.useEmbeddedBackend()) {
            String local = LocalBackend.get(context).awaitReady(20000);
            if (local != null) {
                return local;
            }
        }
        String remote = prefs.getBaseUrl();
        return remote == null || remote.trim().isEmpty() ? "http://127.0.0.1:8420" : remote;
    }

    /** True when requests go to the backend running inside the phone. */
    public boolean isLocal() {
        return prefs.useEmbeddedBackend() && LocalBackend.get(context).baseUrl() != null;
    }

    // ------------------------------------------------------------- HTTP plumbing

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException error) {
            return "";
        }
    }

    private HttpURLConnection open(String method, String path, boolean auth) throws IOException {
        URL url = new URL(baseUrl() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "IndraAI-Android");
        if (auth) {
            String token = prefs.getAccessToken();
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
        }
        return connection;
    }

    private JsonObject send(HttpURLConnection connection, JsonObject body) throws ApiException {
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream out = connection.getOutputStream();
                out.write(body.toJson().getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();
            }
            int status = connection.getResponseCode();
            String text = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status >= 300) {
                throw toException(status, text);
            }
            return text == null || text.trim().isEmpty() ? new JsonObject() : JsonObject.parseOrEmpty(text);
        } catch (IOException error) {
            throw new ApiException(0, "NETWORK_ERROR",
                    "Cannot reach the INDRA engine (" + error.getMessage() + ")", null);
        } finally {
            connection.disconnect();
        }
    }

    private static ApiException toException(int status, String text) {
        String message = "Request failed (" + status + ")";
        String code = "HTTP_" + status;
        String field = null;
        try {
            JsonObject error = JsonObject.parseOrEmpty(text).getObjectOrEmpty("error");
            if (error.has("message")) {
                message = error.getString("message");
            }
            code = error.getString("code", code);
            field = error.getString("field", null);
        } catch (RuntimeException ignored) {
            // keep the generic message
        }
        return new ApiException(status, code, message, field);
    }

    private JsonObject execute(String method, String path, JsonObject body, boolean auth) throws ApiException {
        HttpURLConnection connection = null;
        try {
            connection = open(method, path, auth);
            return send(connection, body);
        } catch (IOException error) {
            throw new ApiException(0, "NETWORK_ERROR", "Cannot reach the INDRA engine", null);
        }
    }

    private JsonObject authed(String method, String path, JsonObject body) throws ApiException {
        try {
            return execute(method, path, body, true);
        } catch (ApiException error) {
            if (!error.isUnauthorized() || prefs.getRefreshToken() == null) {
                throw error;
            }
            if (refreshTokens()) {
                return execute(method, path, body, true);
            }
            throw error;
        }
    }

    /** Exchanges the refresh token; returns false when the session is gone. */
    public boolean refreshTokens() {
        synchronized (refreshLock) {
            String refreshToken = prefs.getRefreshToken();
            if (refreshToken == null || refreshToken.isEmpty()) {
                return false;
            }
            try {
                JsonObject response = execute("POST", "/api/v1/auth/refresh",
                        new JsonObject().put("refreshToken", refreshToken), false);
                String access = response.getString("accessToken", null);
                if (access == null) {
                    return false;
                }
                prefs.saveSession(access, response.getString("refreshToken", refreshToken),
                        response.getObjectOrEmpty("user"));
                return true;
            } catch (ApiException error) {
                Log.warn("ApiClient", "Token refresh failed: " + error.getMessage());
                return false;
            }
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

    // ------------------------------------------------------------------- auth

    /**
     * Opens a session on the on-device engine without an email account.
     * Used by "Continue without signing in" on the login screen.
     */
    public JsonObject localSession(String deviceId, String displayName) throws ApiException {
        JsonObject body = new JsonObject()
                .put("deviceId", deviceId)
                .put("displayName", displayName == null ? "" : displayName);
        return execute("POST", "/api/v1/auth/local", body, false);
    }

    public JsonObject login(String email, String password) throws ApiException {
        return execute("POST", "/api/v1/auth/login",
                new JsonObject().put("email", email).put("password", password), false);
    }

    public JsonObject register(String email, String password, String displayName) throws ApiException {
        return execute("POST", "/api/v1/auth/register", new JsonObject()
                .put("email", email).put("password", password).put("displayName", displayName), false);
    }

    public JsonObject me() throws ApiException {
        return authed("GET", "/api/v1/auth/me", null);
    }

    public JsonObject updateProfile(JsonObject body) throws ApiException {
        return authed("PATCH", "/api/v1/auth/me", body);
    }

    public JsonObject changePassword(String current, String next) throws ApiException {
        return authed("POST", "/api/v1/auth/password",
                new JsonObject().put("currentPassword", current).put("newPassword", next));
    }

    public void logout() {
        try {
            authed("POST", "/api/v1/auth/logout", new JsonObject());
        } catch (ApiException error) {
            Log.warn("ApiClient", "Logout call failed: " + error.getMessage());
        }
    }

    public JsonObject remoteConfig() throws ApiException {
        return execute("GET", "/api/v1/config", null, false);
    }

    public JsonObject oauthStart(String provider, String state, String redirectUri, String codeChallenge)
            throws ApiException {
        return execute("GET", "/api/v1/auth/oauth/" + encode(provider) + "/start?state=" + encode(state)
                + "&redirectUri=" + encode(redirectUri) + "&codeChallenge=" + encode(codeChallenge), null, false);
    }

    public JsonObject oauthExchange(String provider, String code, String redirectUri, String codeVerifier)
            throws ApiException {
        return execute("POST", "/api/v1/auth/oauth/" + encode(provider) + "/exchange",
                new JsonObject().put("code", code).put("redirectUri", redirectUri)
                        .put("codeVerifier", codeVerifier), false);
    }

    public JsonObject activateLicense(String key) throws ApiException {
        return authed("POST", "/api/v1/license/activate", new JsonObject().put("key", key));
    }

    // ------------------------------------------------------------------- chat

    public JsonObject sendMessage(String message, String conversationId, String timeZone, boolean voiceMode)
            throws ApiException {
        return authed("POST", "/api/v1/chat", chatBody(message, conversationId, timeZone, voiceMode));
    }

    private JsonObject chatBody(String message, String conversationId, String timeZone, boolean voiceMode) {
        JsonObject body = new JsonObject()
                .put("message", message)
                .put("timeZone", timeZone)
                .put("voiceMode", Boolean.valueOf(voiceMode))
                .put("personaId", prefs.getPersona())
                .put("brainId", prefs.getBrain())
                .put("languageCode", prefs.getLanguage())
                .put("provider", prefs.getProvider())
                .put("model", prefs.getModel());
        if (conversationId != null) {
            body.put("conversationId", conversationId);
        }
        return body;
    }

    /** Server-Sent-Events streaming. Blocking: call it from an executor. */
    public void streamMessage(JsonObject body, StreamListener listener) {
        HttpURLConnection connection = null;
        try {
            connection = open("POST", "/api/v1/chat/stream", true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            OutputStream out = connection.getOutputStream();
            out.write(body.toJson().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();

            int status = connection.getResponseCode();
            if (status == 401 && refreshTokens()) {
                listener.onError("Your session expired but was renewed - please send the message again.");
                return;
            }
            if (status < 200 || status >= 300) {
                listener.onError(toException(status, readAll(connection.getErrorStream())).getMessage());
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

    // ---------------------------------------------------------- conversations

    public JsonObject conversations() throws ApiException {
        return authed("GET", "/api/v1/conversations?limit=50", null);
    }

    public JsonObject messages(String conversationId) throws ApiException {
        return authed("GET", "/api/v1/conversations/" + encode(conversationId) + "/messages", null);
    }

    public void deleteConversation(String conversationId) throws ApiException {
        authed("DELETE", "/api/v1/conversations/" + encode(conversationId), null);
    }

    // ---------------------------------------------------------------- memory

    public JsonObject memories() throws ApiException {
        return authed("GET", "/api/v1/memories", null);
    }

    public JsonObject createMemory(String content, String category, int importance) throws ApiException {
        return authed("POST", "/api/v1/memories", new JsonObject()
                .put("content", content).put("category", category)
                .put("importance", Integer.valueOf(importance)));
    }

    public JsonObject updateMemory(String id, JsonObject body) throws ApiException {
        return authed("PUT", "/api/v1/memories/" + encode(id), body);
    }

    public void deleteMemory(String id) throws ApiException {
        authed("DELETE", "/api/v1/memories/" + encode(id), null);
    }

    public JsonObject clearMemories() throws ApiException {
        return authed("DELETE", "/api/v1/memories", null);
    }

    // ----------------------------------------------------------------- tasks

    public JsonObject tasks(boolean includeCompleted) throws ApiException {
        return authed("GET", "/api/v1/tasks?includeCompleted=" + includeCompleted, null);
    }

    public JsonObject createTask(JsonObject body) throws ApiException {
        return authed("POST", "/api/v1/tasks", body);
    }

    public JsonObject updateTask(String id, JsonObject body) throws ApiException {
        return authed("PUT", "/api/v1/tasks/" + encode(id), body);
    }

    public JsonObject completeTask(String id, String timeZone) throws ApiException {
        return authed("POST", "/api/v1/tasks/" + encode(id) + "/complete",
                new JsonObject().put("timeZone", timeZone));
    }

    public void deleteTask(String id) throws ApiException {
        authed("DELETE", "/api/v1/tasks/" + encode(id), null);
    }

    // -------------------------------------------------------------- settings

    public JsonObject settings() throws ApiException {
        return authed("GET", "/api/v1/settings", null);
    }

    public JsonObject saveSettings(JsonObject body) throws ApiException {
        return authed("PUT", "/api/v1/settings", body);
    }

    public JsonObject settingsCatalogue() throws ApiException {
        return authed("GET", "/api/v1/settings/catalogue", null);
    }

    // ------------------------------------------------------------ API keys

    public JsonObject keys() throws ApiException {
        return authed("GET", "/api/v1/keys", null);
    }

    public JsonObject addKey(String provider, String label, String key, boolean permanent) throws ApiException {
        return authed("POST", "/api/v1/keys", new JsonObject()
                .put("provider", provider)
                .put("label", label == null ? "" : label)
                .put("key", key)
                .put("permanent", Boolean.valueOf(permanent)));
    }

    public void removeKey(String id) throws ApiException {
        authed("DELETE", "/api/v1/keys/" + encode(id), null);
    }

    public JsonObject toggleKey(String id, boolean active) throws ApiException {
        return authed("POST", "/api/v1/keys/" + encode(id) + "/toggle",
                new JsonObject().put("active", Boolean.valueOf(active)));
    }

    public JsonObject resetKeys(String provider) throws ApiException {
        return authed("POST", "/api/v1/keys/reset",
                new JsonObject().put("provider", provider == null ? "" : provider));
    }

    // --------------------------------------------------------------- devices

    public JsonObject devices() throws ApiException {
        return authed("GET", "/api/v1/devices", null);
    }

    public JsonObject startPairing(String deviceName, String platform) throws ApiException {
        return authed("POST", "/api/v1/devices/pair/start",
                new JsonObject().put("name", deviceName).put("platform", platform));
    }

    public JsonObject completePairing(String code, String deviceName, String platform) throws ApiException {
        return authed("POST", "/api/v1/devices/pair/complete", new JsonObject()
                .put("code", code).put("name", deviceName).put("platform", platform));
    }

    public void revokeDevice(String id) throws ApiException {
        authed("DELETE", "/api/v1/devices/" + encode(id), null);
    }

    public JsonObject heartbeat(String deviceId) throws ApiException {
        return authed("POST", "/api/v1/devices/heartbeat",
                new JsonObject().put("deviceId", deviceId));
    }

    public JsonObject sync(JsonObject envelope) throws ApiException {
        return authed("POST", "/api/v1/sync", envelope);
    }

    // ----------------------------------------------------------------- tools

    public JsonObject weather(String location, Double lat, Double lon) throws ApiException {
        StringBuilder path = new StringBuilder("/api/v1/weather?");
        if (location != null && !location.trim().isEmpty()) {
            path.append("location=").append(encode(location.trim()));
        }
        if (lat != null && lon != null) {
            if (path.charAt(path.length() - 1) != '?') {
                path.append('&');
            }
            path.append("lat=").append(lat.doubleValue()).append("&lon=").append(lon.doubleValue());
        }
        return authed("GET", path.toString(), null);
    }

    public JsonObject search(String query, boolean news) throws ApiException {
        return authed("GET", "/api/v1/search?q=" + encode(query) + "&news=" + news + "&limit=5", null);
    }

    // ----------------------------------------------------------------- admin

    public JsonObject adminUsers(String query) throws ApiException {
        return authed("GET", "/api/v1/admin/users?limit=100&q=" + encode(query == null ? "" : query), null);
    }

    public JsonObject adminUpdateUser(String id, JsonObject body) throws ApiException {
        return authed("PATCH", "/api/v1/admin/users/" + encode(id), body);
    }

    public JsonObject adminDeleteUser(String id) throws ApiException {
        return authed("DELETE", "/api/v1/admin/users/" + encode(id), null);
    }

    public JsonObject adminConfig() throws ApiException {
        return authed("GET", "/api/v1/admin/config", null);
    }

    public JsonObject adminSaveConfig(JsonObject body) throws ApiException {
        return authed("PUT", "/api/v1/admin/config", body);
    }

    public JsonObject adminAudit() throws ApiException {
        return authed("GET", "/api/v1/admin/audit?limit=100", null);
    }

    public JsonObject adminStats() throws ApiException {
        return authed("GET", "/api/v1/admin/stats", null);
    }

    public JsonObject adminLicenses() throws ApiException {
        return authed("GET", "/api/v1/admin/licenses", null);
    }

    public JsonObject adminIssueLicense(String plan, int count) throws ApiException {
        return authed("POST", "/api/v1/admin/licenses", new JsonObject()
                .put("plan", plan == null || plan.isEmpty() ? "personal" : plan)
                .put("count", Integer.valueOf(Math.max(1, count))));
    }

    public JsonObject adminRevokeLicense(String key) throws ApiException {
        return authed("POST", "/api/v1/admin/licenses/" + encode(key) + "/revoke", new JsonObject());
    }
}
