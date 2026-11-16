package ai.indra.desktop;

import ai.indra.core.util.Log;
import ai.indra.core.util.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Desktop configuration stored per user under %APPDATA%\IndraAI (Windows) or
 * ~/.config/indra-ai (Linux/macOS).
 *
 * <p>Security note: the auth token is never written to disk. Only the server URL,
 * device identity and UI preferences are persisted, so a stolen config file does
 * not expose an account. Users sign in again after closing the app (or after the
 * token expires), which is the safer default for a PC that may be shared.</p>
 */
public final class DesktopConfig {

    private final Path file;
    private final Properties properties = new Properties();

    private String accessToken;
    private String refreshToken;

    public DesktopConfig() {
        this.file = defaultPath();
        load();
    }

    public static Path directory() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.trim().isEmpty()) {
            return Paths.get(appData, "IndraAI");
        }
        return Paths.get(System.getProperty("user.home"), ".config", "indra-ai");
    }

    private static Path defaultPath() {
        return directory().resolve("client.properties");
    }

    private void load() {
        if (!Files.exists(file)) {
            properties.setProperty("baseUrl", "http://127.0.0.1:8420");
            properties.setProperty("deviceName", defaultDeviceName());
            properties.setProperty("language", "en");
            properties.setProperty("persona", "friendly");
            properties.setProperty("brain", "core");
            properties.setProperty("autoSpeak", "true");
            return;
        }
        InputStream input = null;
        try {
            input = Files.newInputStream(file);
            properties.load(input);
        } catch (IOException error) {
            Log.warn("DesktopConfig", "Could not read config: " + error.getMessage());
        } finally {
            closeQuietly(input);
        }
    }

    private static String defaultDeviceName() {
        String name = System.getenv("COMPUTERNAME");
        return name == null || name.trim().isEmpty() ? "Windows PC" : name;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            OutputStream out = Files.newOutputStream(file);
            try {
                properties.store(out, "INDRA AI desktop settings (no credentials are stored)");
            } finally {
                out.close();
            }
            try {
                // Owner-only permissions on POSIX systems; on Windows the profile is already per-user.
                Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Windows has no POSIX permissions - the per-user AppData folder is the boundary.
            }
        } catch (IOException error) {
            Log.warn("DesktopConfig", "Could not save config: " + error.getMessage());
        }
    }

    public String getBaseUrl() {
        return properties.getProperty("baseUrl", "http://127.0.0.1:8420");
    }

    public void setBaseUrl(String url) {
        String cleaned = Strings.orEmpty(url).trim();
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        properties.setProperty("baseUrl", cleaned.isEmpty() ? "http://127.0.0.1:8420" : cleaned);
        save();
    }

    public String getDeviceName() {
        return properties.getProperty("deviceName", defaultDeviceName());
    }

    public void setDeviceName(String name) {
        properties.setProperty("deviceName", name);
        save();
    }

    public String getLanguage() {
        return properties.getProperty("language", "en");
    }

    public void setLanguage(String language) {
        properties.setProperty("language", language);
        save();
    }

    public String getPersona() {
        return properties.getProperty("persona", "friendly");
    }

    public void setPersona(String persona) {
        properties.setProperty("persona", persona);
        save();
    }

    public String getBrain() {
        return properties.getProperty("brain", "core");
    }

    public void setBrain(String brain) {
        properties.setProperty("brain", brain);
        save();
    }

    public String getProvider() {
        return properties.getProperty("provider", "");
    }

    public String getModel() {
        return properties.getProperty("model", "");
    }

    public void setProvider(String provider, String model) {
        properties.setProperty("provider", provider);
        properties.setProperty("model", model);
        save();
    }

    public boolean isAutoSpeak() {
        return Boolean.parseBoolean(properties.getProperty("autoSpeak", "true"));
    }

    public void setAutoSpeak(boolean autoSpeak) {
        properties.setProperty("autoSpeak", Boolean.toString(autoSpeak));
        save();
    }

    public String getWakeWord() {
        return properties.getProperty("wakeWord", "indra");
    }

    public void setWakeWord(String word) {
        properties.setProperty("wakeWord", word);
        save();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setSession(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public void clearSession() {
        this.accessToken = null;
        this.refreshToken = null;
    }

    public boolean hasSession() {
        return accessToken != null && !accessToken.isEmpty();
    }

    private static void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }
}
