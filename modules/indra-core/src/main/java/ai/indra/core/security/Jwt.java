package ai.indra.core.security;

import ai.indra.core.json.Json;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Ids;

/** Minimal HS256 JSON Web Token implementation (sign + verify). */
public final class Jwt {

    /** Raised for expired, malformed or badly signed tokens. */
    public static class TokenException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public TokenException(String message) {
            super(message);
        }
    }

    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] secret;
    private final String issuer;
    private final long ttlSeconds;

    public Jwt(String secret, String issuer, long ttlSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.secret = secret.getBytes(Crypto.UTF8);
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds <= 0 ? 3600L : ttlSeconds;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /** Signs the claim set, filling in iss/iat/exp/jti when absent. */
    public String sign(JsonObject claims) {
        JsonObject payload = new JsonObject(claims);
        long now = System.currentTimeMillis() / 1000L;
        if (!payload.has("iss")) {
            payload.put("iss", issuer);
        }
        if (!payload.has("iat")) {
            payload.put("iat", Long.valueOf(now));
        }
        if (!payload.has("exp")) {
            payload.put("exp", Long.valueOf(now + ttlSeconds));
        }
        if (!payload.has("jti")) {
            payload.put("jti", Ids.random(16));
        }
        String header = Crypto.base64UrlEncode(HEADER.getBytes(Crypto.UTF8));
        String body = Crypto.base64UrlEncode(payload.toJson().getBytes(Crypto.UTF8));
        String signature = Crypto.base64UrlEncode(Crypto.hmacSha256(secret, (header + "." + body).getBytes(Crypto.UTF8)));
        return header + "." + body + "." + signature;
    }

    /** Verifies signature, issuer and expiry, returning the payload. */
    public JsonObject verify(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new TokenException("Missing token");
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new TokenException("Malformed token");
        }
        String expected = Crypto.base64UrlEncode(
                Crypto.hmacSha256(secret, (parts[0] + "." + parts[1]).getBytes(Crypto.UTF8)));
        if (!Crypto.constantTimeEquals(expected, parts[2])) {
            throw new TokenException("Invalid token signature");
        }
        JsonObject payload;
        try {
            payload = JsonObject.parse(new String(Crypto.base64UrlDecode(parts[1]), Crypto.UTF8));
        } catch (RuntimeException error) {
            throw new TokenException("Malformed token payload");
        }
        long now = System.currentTimeMillis() / 1000L;
        long expiresAt = payload.getLong("exp", 0L);
        if (expiresAt > 0 && now > expiresAt) {
            throw new TokenException("Token expired");
        }
        String tokenIssuer = payload.getString("iss", "");
        if (issuer != null && !issuer.isEmpty() && !issuer.equals(tokenIssuer)) {
            throw new TokenException("Invalid token issuer");
        }
        return payload;
    }

    /** Returns the payload without verification, or null when unparseable. */
    public static JsonObject peek(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            return JsonObject.parse(new String(Crypto.base64UrlDecode(parts[1]), Crypto.UTF8));
        } catch (RuntimeException error) {
            return null;
        }
    }
}
