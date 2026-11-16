package ai.indra.backend.http;

import ai.indra.core.json.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable HTTP response. */
public final class Response {

    private final int status;
    private final String contentType;
    private final byte[] body;
    private final Map<String, String> headers;

    private Response(int status, String contentType, byte[] body, Map<String, String> headers) {
        this.status = status;
        this.contentType = contentType;
        this.body = body == null ? new byte[0] : body;
        this.headers = headers == null ? new LinkedHashMap<String, String>() : headers;
    }

    public static Response json(int status, JsonObject body) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Cache-Control", "no-store");
        return new Response(status, "application/json; charset=utf-8",
                body == null ? "{}".getBytes(ai.indra.core.security.Crypto.UTF8)
                        : body.toJson().getBytes(ai.indra.core.security.Crypto.UTF8), headers);
    }

    public static Response ok(JsonObject body) {
        return json(200, body);
    }

    public static Response created(JsonObject body) {
        return json(201, body);
    }

    public static Response noContent() {
        return new Response(204, "text/plain", new byte[0], null);
    }

    public static Response text(int status, String body) {
        return new Response(status, "text/plain; charset=utf-8",
                body.getBytes(ai.indra.core.security.Crypto.UTF8), null);
    }

    public static Response html(int status, String body) {
        return new Response(status, "text/html; charset=utf-8",
                body.getBytes(ai.indra.core.security.Crypto.UTF8), null);
    }

    public static Response stream(int status, String contentType) {
        return new Response(status, contentType, null, null);
    }

    public Response header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}
