package ai.indra.backend.http;

import ai.indra.backend.BackendConfig;
import ai.indra.core.model.ApiError;
import ai.indra.core.model.User;
import ai.indra.core.security.Crypto;
import ai.indra.core.security.ValidationException;
import ai.indra.core.util.Ids;
import ai.indra.core.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP front end: routing, auth, CORS, rate limiting and error mapping. */
public final class HttpServerApp {

    /** Resolves a bearer token to a user (implemented by the auth service). */
    public interface Authenticator {
        User authenticate(String token);
    }

    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private final Router router;
    private final BackendConfig config;
    private final Authenticator authenticator;
    private final RateLimiter apiLimiter;
    private final RateLimiter authLimiter;
    private LocalHttpServer server;

    public HttpServerApp(Router router, BackendConfig config, Authenticator authenticator) {
        this.router = router;
        this.config = config;
        this.authenticator = authenticator;
        this.apiLimiter = new RateLimiter(config.getRateLimitPerMinute());
        this.authLimiter = new RateLimiter(config.getAuthRateLimitPerMinute());
    }

    public void start() throws IOException {
        server = new LocalHttpServer(config.getBindAddress(), config.getPort(), new LocalHttpServer.RequestHandler() {
            @Override
            public void handle(Exchange exchange) {
                HttpServerApp.this.handle(exchange);
            }
        });
        server.start();
        Log.info("HttpServerApp", "INDRA AI backend listening on " + config.getBindAddress() + ":" + getPort()
                + " (" + router.size() + " routes)");
    }

    /** Port actually bound (the on-device backend asks for a free port). */
    public int getPort() {
        return server == null ? config.getPort() : server.getPort();
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private void handle(Exchange exchange) {
        String requestId = Ids.random(10);
        long started = System.currentTimeMillis();
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            Map<String, String> headers = exchange.getRequestHeaders().asMap();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                applyCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String clientIp = clientIp(exchange);
            String bucket = path.startsWith("/api/v1/auth/") ? "auth" : "api";
            RateLimiter limiter = "auth".equals(bucket) ? authLimiter : apiLimiter;
            if (!limiter.allow(bucket + ":" + clientIp)) {
                write(exchange, Response.json(429,
                        ApiError.rateLimited("Too many requests - slow down and try again shortly").withRequestId(requestId).toJson()));
                return;
            }

            Router.Match match = router.match(method, path);
            if (match == null) {
                write(exchange, Response.json(404,
                        ApiError.notFound("Unknown endpoint: " + method + " " + path).withRequestId(requestId).toJson()));
                return;
            }

            User user = null;
            if (match.route.getAuth() != Router.Auth.PUBLIC) {
                String token = bearer(exchange);
                if (token == null) {
                    write(exchange, Response.json(401,
                            ApiError.unauthorized("Sign in to continue").withRequestId(requestId).toJson()));
                    return;
                }
                try {
                    user = authenticator.authenticate(token);
                } catch (ApiException error) {
                    write(exchange, Response.json(error.getStatus(),
                            new ApiError(error.getStatus(), error.getCode(), error.getMessage(), error.getField(), requestId).toJson()));
                    return;
                }
                if (user == null) {
                    write(exchange, Response.json(401,
                            ApiError.unauthorized("Session expired - please sign in again").withRequestId(requestId).toJson()));
                    return;
                }
                if (match.route.getAuth() == Router.Auth.ADMIN && !user.isAdmin()) {
                    write(exchange, Response.json(403,
                            ApiError.forbidden("Administrator access required").withRequestId(requestId).toJson()));
                    return;
                }
            }

            byte[] body = readBody(exchange);
            RequestContext context = new RequestContext(method, path, query, match.params, headers, body,
                    clientIp, requestId, user);
            context.setAttribute("sse", new SseResponder(exchange));

            Response response = match.route.handler.handle(context);
            if (response != null) {
                applyCors(exchange);
                write(exchange, response);
            }
            Log.debug("HttpServerApp", method + " " + path + " -> " + (response == null ? "streamed" : String.valueOf(response.getStatus()))
                    + " in " + (System.currentTimeMillis() - started) + "ms");
        } catch (ApiException error) {
            write(exchange, Response.json(error.getStatus(),
                    new ApiError(error.getStatus(), error.getCode(), error.getMessage(), error.getField(), requestId).toJson()));
        } catch (ValidationException error) {
            write(exchange, Response.json(400,
                    new ApiError(400, ApiError.VALIDATION_FAILED, error.getMessage(), error.getField(), requestId).toJson()));
        } catch (ai.indra.core.json.JsonException error) {
            write(exchange, Response.json(400,
                    new ApiError(400, ApiError.BAD_REQUEST, "Malformed JSON body", null, requestId).toJson()));
        } catch (Throwable error) {
            Log.error("HttpServerApp", "Unhandled error on " + exchange.getRequestURI().getPath() + " [" + requestId + "]", error);
            String message = config.isDebug()
                    ? error.getClass().getSimpleName() + ": " + error.getMessage()
                    : "Something went wrong on the server. Reference: " + requestId;
            write(exchange, Response.json(500,
                    new ApiError(500, ApiError.INTERNAL, message, null, requestId).toJson()));
        } finally {
            try {
                exchange.close();
            } catch (RuntimeException ignored) {
                // already closed by a streaming handler
            }
        }
    }

    private void write(Exchange exchange, Response response) {
        try {
            HeaderMap headers = exchange.getResponseHeaders();
            for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
                headers.set(entry.getKey(), entry.getValue());
            }
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
            byte[] body = response.getBody();
            if (response.getStatus() == 204) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            headers.set("Content-Type", response.getContentType());
            exchange.sendResponseHeaders(response.getStatus(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                OutputStream out = exchange.getResponseBody();
                out.write(body);
                out.flush();
            }
        } catch (IOException error) {
            Log.warn("HttpServerApp", "Could not write response: " + error.getMessage());
        }
    }

    private void applyCors(Exchange exchange) {
        String origins = config.getCorsOrigins();
        HeaderMap headers = exchange.getResponseHeaders();
        if (origins == null || origins.trim().isEmpty() || "*".equals(origins.trim())) {
            headers.set("Access-Control-Allow-Origin", "*");
        } else {
            String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
            for (String allowed : origins.split(",")) {
                if (allowed.trim().equalsIgnoreCase(requestOrigin)) {
                    headers.set("Access-Control-Allow-Origin", requestOrigin);
                    headers.set("Vary", "Origin");
                    break;
                }
            }
        }
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Device-Id");
        headers.set("Access-Control-Max-Age", "600");
    }

    private String bearer(Exchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return null;
    }

    private byte[] readBody(Exchange exchange) throws IOException {
        InputStream input = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BODY_BYTES) {
                throw ApiException.badRequest("Request body too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            String key = index >= 0 ? pair.substring(0, index) : pair;
            String value = index >= 0 ? pair.substring(index + 1) : "";
            try {
                query.put(java.net.URLDecoder.decode(key, "UTF-8"), java.net.URLDecoder.decode(value, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException error) {
                query.put(key, value);
            }
        }
        return query;
    }

    private static String clientIp(Exchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRemoteAddress() == null ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
    }
}
