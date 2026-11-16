package ai.indra.backend.http;

import ai.indra.core.json.JsonObject;
import ai.indra.core.security.Crypto;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;

/** Server-Sent-Events writer used for streaming chat responses. */
public final class SseResponder {

    private final HttpExchange exchange;
    private OutputStream out;
    private boolean started;
    private boolean closed;

    public SseResponder(HttpExchange exchange) {
        this.exchange = exchange;
    }

    public void start() throws IOException {
        if (started) {
            return;
        }
        started = true;
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
        exchange.sendResponseHeaders(200, 0);
        out = exchange.getResponseBody();
    }

    public void event(String name, JsonObject data) throws IOException {
        start();
        if (closed) {
            return;
        }
        StringBuilder frame = new StringBuilder();
        frame.append("event: ").append(name).append('\n');
        frame.append("data: ").append(data.toJson()).append('\n');
        frame.append('\n');
        out.write(frame.toString().getBytes(Crypto.UTF8));
        out.flush();
    }

    public void token(String delta) throws IOException {
        event("token", new JsonObject().put("delta", delta));
    }

    public void done(String fullText, JsonObject meta) throws IOException {
        JsonObject payload = new JsonObject();
        payload.put("text", fullText);
        payload.merge(meta);
        event("done", payload);
        close();
    }

    public void fail(String code, String message) throws IOException {
        JsonObject payload = new JsonObject();
        payload.put("code", code);
        payload.put("message", message);
        event("error", payload);
        close();
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (out != null) {
                out.flush();
            }
            exchange.close();
        } catch (IOException ignored) {
            // client disconnected
        }
    }
}
