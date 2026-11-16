package ai.indra.backend;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend configuration from environment variables (never hard-coded secrets).
 *
 * <p>See {@code config/backend.env.example} for the full list. Anything without
 * a value is either optional (AI providers, OAuth) or generated once and stored
 * with owner-only permissions inside the data directory.</p>
 */
public final class BackendConfig {

    private final Map<String, String> values = new LinkedHashMap<String, String>();
    private final File dataDir;
    private String jwtSecret;
    private String masterKey;
    private String bootstrapAdminPassword;

    private BackendConfig(Map<String, String> values) {
        this.values = values;
        this.dataDir = new File(get("INDRA_DATA_DIR", defaultDataDir()));
    }

    public static BackendConfig fromEnvironment() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            values.put(entry.getKey(), entry.getValue());
        }
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("indra.")) {
                values.put(key.toUpperCase(java.util.Locale.ROOT).replace('.', '_').replace("INDRA_", "INDRA_"),
                        System.getProperty(key));
            }
        }
        return new BackendConfig(values);
    }

    /** Builds a configuration from an explicit map (tests, admin tooling). */
    public static BackendConfig fromMap(java.util.Map<String, String> source) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (source != null) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    values.put(entry.getKey().toUpperCase(java.util.Locale.ROOT), entry.getValue());
                }
            }
        }
        return new BackendConfig(values);
    }

    private static String defaultDataDir() {
        String home = System.getProperty("user.home", ".");
        return new File(home, ".indra-ai").getAbsolutePath();
    }

    public String get(String key, String fallback) {
        String value = values.get(key);
        if (value == null) {
            value = values.get(key.toUpperCase(java.util.Locale.ROOT));
        }
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }

    public File getDataDir() {
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IllegalStateException("Cannot create data directory: " + dataDir.getAbsolutePath());
        }
        return dataDir;
    }

    public int getPort() {
        return getInt("INDRA_PORT", 8420);
    }

    public String getBindAddress() {
        return get("INDRA_BIND", "0.0.0.0");
    }

    public String getPublicBaseUrl() {
        return get("INDRA_PUBLIC_URL", "http://127.0.0.1:" + getPort());
    }

    public boolean isTlsEnabled() {
        return get("INDRA_TLS_KEYSTORE", null) != null;
    }

    public String getKeystorePath() {
        return get("INDRA_TLS_KEYSTORE", null);
    }

    public char[] getKeystorePassword() {
        String password = get("INDRA_TLS_PASSWORD", null);
        return password == null ? null : password.toCharArray();
    }

    public String getDatabaseUrl() {
        return get("INDRA_DB_URL", null);
    }

    public String getDatabaseUser() {
        return get("INDRA_DB_USER", null);
    }

    public String getDatabasePassword() {
        return get("INDRA_DB_PASSWORD", null);
    }

    /** HS256 signing secret; generated once and persisted outside the DB. */
    public String getJwtSecret() {
        if (jwtSecret == null) {
            String configured = get("INDRA_JWT_SECRET", null);
            jwtSecret = configured != null && configured.length() >= 32
                    ? configured : persistedSecret("jwt.key", 48);
        }
        return jwtSecret;
    }

    /** Master key used to encrypt per-user API keys at rest. */
    public String getMasterKey() {
        if (masterKey == null) {
            String configured = get("INDRA_MASTER_KEY", null);
            masterKey = configured != null && configured.length() >= 32
                    ? configured : persistedSecret("master.key", 32);
        }
        return masterKey;
    }

    /** One-time admin bootstrap password (rotated immediately after first login). */
    public String getBootstrapAdminPassword() {
        if (bootstrapAdminPassword == null) {
            bootstrapAdminPassword = get("INDRA_ADMIN_PASSWORD", null);
        }
        return bootstrapAdminPassword;
    }

    public String getBootstrapAdminEmail() {
        return get("INDRA_ADMIN_EMAIL", null);
    }

    public String getLong(String key, long fallback) {
        try {
            return Long.parseLong(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    /** Reads or creates a random secret file with owner-only permissions. */
    private String persistedSecret(String fileName, int byteLength) {
        File file = new File(getDataDir(), fileName);
        try {
            if (file.exists()) {
                String existing = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (existing.length() >= 32) {
                    return existing;
                }
            }
            byte[] random = new byte[byteLength];
            new SecureRandom().nextBytes(random);
            String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            Files.write(file.toPath(), secret.getBytes(StandardCharsets.UTF_8));
            try {
                Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows: rely on the per-user data directory ACL.
            }
            return secret;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot persist secret " + fileName, error);
        }
    }

    // ---- AI provider keys held by the server (optional: users can bring their own) ----

    public String providerKey(String providerId) {
        String env = ai.indra.core.ai.ProviderCatalog.get(providerId).getEnvVar();
        if (env == null || env.isEmpty()) {
            return null;
        }
        String value = get(env, null);
        return value == null ? null : value;
    }

    public String providerBaseUrl(String providerId) {
        return get("INDRA_" + providerId.toUpperCase(java.util.Locale.ROOT) + "_BASE_URL", null);
    }

    // ---- OAuth ----

    public String googleClientId() {
        return get("GOOGLE_CLIENT_ID", null);
    }

    public String googleClientSecret() {
        return get("GOOGLE_CLIENT_SECRET", null);
    }

    public String githubClientId() {
        return get("GITHUB_CLIENT_ID", null);
    }

    public String githubClientSecret() {
        return get("GITHUB_CLIENT_SECRET", null);
    }

    public String getCorsOrigins() {
        return get("INDRA_CORS_ORIGINS", "*");
    }

    public int getRateLimitPerMinute() {
        return getInt("INDRA_RATE_LIMIT", 120);
    }

    public int getAuthRateLimitPerMinute() {
        return getInt("INDRA_AUTH_RATE_LIMIT", 20);
    }

    public String getWeatherBaseUrl() {
        return get("INDRA_WEATHER_URL", "https://api.open-meteo.com/v1");
    }

    public String getGeocodeBaseUrl() {
        return get("INDRA_GEOCODE_URL", "https://geocoding-api.open-meteo.com/v1");
    }

    public String getTavilyKey() {
        return get("TAVILY_API_KEY", null);
    }

    public boolean isDebug() {
        return getBoolean("INDRA_DEBUG", false);
    }
}
