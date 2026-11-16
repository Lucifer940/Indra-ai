package ai.indra.backend.http;

import ai.indra.core.json.JsonObject;
import ai.indra.core.model.User;
import ai.indra.core.security.Crypto;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-request state handed to route handlers. */
public final class RequestContext {

    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> pathParams;
    private final Map<String, String> headers;
    private final byte[] rawBody;
    private final String remoteAddress;
    private final String requestId;
    private final JsonObject body;
    private final User user;
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    public RequestContext(String method, String path, Map<String, String> query, Map<String, String> pathParams,
                          Map<String, String> headers, byte[] rawBody, String remoteAddress, String requestId,
                          User user) {
        this.method = method;
        this.path = path;
        this.query = query == null ? new LinkedHashMap<String, String>() : query;
        this.pathParams = pathParams == null ? new LinkedHashMap<String, String>() : pathParams;
        this.headers = headers == null ? new LinkedHashMap<String, String>() : headers;
        this.rawBody = rawBody == null ? new byte[0] : rawBody;
        this.remoteAddress = remoteAddress;
        this.requestId = requestId;
        this.user = user;
        String text = new String(this.rawBody, Crypto.UTF8).trim();
        this.body = text.isEmpty() ? new JsonObject() : JsonObject.parse(text);
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getQuery() {
        return query;
    }

    public String query(String name) {
        return query.get(name);
    }

    public String query(String name, String fallback) {
        String value = query.get(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public String header(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public JsonObject body() {
        return body;
    }

    public byte[] rawBody() {
        return rawBody;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getRequestId() {
        return requestId;
    }

    /** Authenticated user, or null for public endpoints. */
    public User getUser() {
        return user;
    }

    public User requireUser() {
        if (user == null) {
            throw ApiException.unauthorized("Sign in to continue");
        }
        return user;
    }

    public String getUserId() {
        return requireUser().getId();
    }

    public boolean isAdmin() {
        return user != null && user.isAdmin();
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /** Bearer token from the Authorization header, or null. */
    public String bearerToken() {
        String header = header("Authorization");
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return null;
    }

    public String clientIp() {
        String forwarded = header("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return remoteAddress == null ? "unknown" : remoteAddress;
    }
}
