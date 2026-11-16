package ai.indra.backend.service;

import ai.indra.backend.BackendConfig;
import ai.indra.backend.http.ApiException;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.AuthProvider;
import ai.indra.core.model.User;
import ai.indra.core.util.Log;
import ai.indra.core.util.Strings;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Google and GitHub sign-in using the OAuth 2.0 authorisation-code flow with
 * PKCE. Client secrets stay on the server; the apps only ever see the code.
 *
 * <p>If the operator has not configured a provider, the endpoint answers 503 with
 * a precise message instead of pretending sign-in works.</p>
 */
public final class OAuthService {

    private final BackendConfig config;
    private final AuthService authService;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public OAuthService(BackendConfig config, AuthService authService) {
        this.config = config;
        this.authService = authService;
    }

    public boolean isConfigured(String provider) {
        if ("google".equalsIgnoreCase(provider)) {
            return config.googleClientId() != null && config.googleClientSecret() != null;
        }
        if ("github".equalsIgnoreCase(provider)) {
            return config.githubClientId() != null && config.githubClientSecret() != null;
        }
        return false;
    }

    /** URL the client opens in a browser or Custom Tab. */
    public JsonObject authorizeUrl(String provider, String state, String redirectUri, String codeChallenge) {
        if (!isConfigured(provider)) {
            throw ApiException.unavailable(providerLabel(provider) + " sign-in is not configured on this server. "
                    + "Set " + envPrefix(provider) + "_CLIENT_ID and " + envPrefix(provider)
                    + "_CLIENT_SECRET, then enable it in Admin → Configuration.");
        }
        String base;
        String clientId;
        String scope;
        if ("google".equalsIgnoreCase(provider)) {
            base = "https://accounts.google.com/o/oauth2/v2/auth";
            clientId = config.googleClientId();
            scope = "openid email profile";
        } else {
            base = "https://github.com/login/oauth/authorize";
            clientId = config.githubClientId();
            scope = "read:user user:email";
        }
        StringBuilder url = new StringBuilder(base);
        url.append("?client_id=").append(encode(clientId));
        url.append("&redirect_uri=").append(encode(redirectUri));
        url.append("&response_type=code");
        url.append("&scope=").append(encode(scope));
        url.append("&state=").append(encode(state));
        if (!Strings.isBlank(codeChallenge)) {
            url.append("&code_challenge=").append(encode(codeChallenge));
            url.append("&code_challenge_method=S256");
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("provider", provider);
        response.put("url", url.toString());
        response.put("state", state);
        return response;
    }

    /** Exchanges an authorisation code and returns an INDRA AI session. */
    public JsonObject exchange(String provider, String code, String redirectUri, String codeVerifier) {
        if (!isConfigured(provider)) {
            throw ApiException.unavailable(providerLabel(provider) + " sign-in is not configured on this server");
        }
        if (Strings.isBlank(code)) {
            throw ApiException.badRequest("Missing authorisation code");
        }
        JsonObject tokens = exchangeCode(provider, code, redirectUri, codeVerifier);
        String accessToken = tokens.getString("access_token");
        if (Strings.isBlank(accessToken)) {
            throw new ApiException(502, "oauth_failed", providerLabel(provider) + " did not return an access token");
        }
        String subject;
        String email;
        String name;
        String photo;
        if ("google".equalsIgnoreCase(provider)) {
            JsonObject profile = getJson("https://openidconnect.googleapis.com/v1/userinfo", accessToken);
            subject = profile.getString("sub");
            email = profile.getString("email");
            name = profile.getString("name");
            photo = profile.getString("picture");
        } else {
            JsonObject profile = getJson("https://api.github.com/user", accessToken);
            subject = profile.getString("id");
            name = profile.getString("name") == null ? profile.getString("login") : profile.getString("name");
            photo = profile.getString("avatarUrl") == null ? profile.getString("avatar_url") : profile.getString("avatarUrl");
            email = profile.getString("email");
            if (Strings.isBlank(email)) {
                email = primaryGithubEmail(accessToken);
            }
        }
        AuthProvider authProvider = "google".equalsIgnoreCase(provider) ? AuthProvider.GOOGLE : AuthProvider.GITHUB;
        User user = authService.upsertFederatedUser(authProvider, subject, email, name, photo);
        return authService.federatedSession(user);
    }

    private String primaryGithubEmail(String accessToken) {
        try {
            JsonObject response = getJsonObject("https://api.github.com/user/emails", accessToken);
            JsonArray emails = response.getArrayOrEmpty("emails");
            for (int i = 0; i < emails.size(); i++) {
                JsonObject entry = emails.getObject(i);
                if (entry != null && entry.getBoolean("primary", false)) {
                    return entry.getString("email");
                }
            }
        } catch (RuntimeException error) {
            Log.warn("OAuthService", "Could not read GitHub emails: " + error.getMessage());
        }
        return null;
    }

    private JsonObject exchangeCode(String provider, String code, String redirectUri, String codeVerifier) {
        String url;
        JsonObject payload = new JsonObject();
        if ("google".equalsIgnoreCase(provider)) {
            url = "https://oauth2.googleapis.com/token";
            payload.put("client_id", config.googleClientId());
            payload.put("client_secret", config.googleClientSecret());
            payload.put("code", code);
            payload.put("grant_type", "authorization_code");
            payload.put("redirect_uri", redirectUri);
            if (!Strings.isBlank(codeVerifier)) {
                payload.put("code_verifier", codeVerifier);
            }
        } else {
            url = "https://github.com/login/oauth/access_token";
            payload.put("client_id", config.githubClientId());
            payload.put("client_secret", config.githubClientSecret());
            payload.put("code", code);
            payload.put("redirect_uri", redirectUri);
        }
        return postForm(url, payload);
    }

    private JsonObject postForm(String url, JsonObject form) {
        try {
            StringBuilder body = new StringBuilder();
            for (String key : form.keySet()) {
                if (body.length() > 0) {
                    body.append('&');
                }
                body.append(encode(key)).append('=').append(encode(String.valueOf(form.get(key))));
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .header("User-Agent", "IndraAI/1.1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(502, "oauth_failed", "Sign-in provider rejected the code");
            }
            return JsonObject.parse(response.body());
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(502, "oauth_unavailable", "Cannot reach the sign-in provider right now");
        }
    }

    private JsonObject getJson(String url, String accessToken) {
        JsonObject result = getJsonObject(url, accessToken);
        return result;
    }

    private JsonObject getJsonObject(String url, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("User-Agent", "IndraAI/1.1.0")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(502, "oauth_failed", "Sign-in provider error " + response.statusCode());
            }
            Object parsed = ai.indra.core.json.Json.parse(response.body());
            if (parsed instanceof JsonObject) {
                return (JsonObject) parsed;
            }
            if (parsed instanceof JsonArray) {
                return new JsonObject().put("emails", parsed);
            }
            return new JsonObject();
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(502, "oauth_unavailable", "Cannot reach the sign-in provider right now");
        }
    }

    private static String providerLabel(String provider) {
        return "github".equalsIgnoreCase(provider) ? "GitHub" : "Google";
    }

    private static String envPrefix(String provider) {
        return "github".equalsIgnoreCase(provider) ? "GITHUB" : "GOOGLE";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
