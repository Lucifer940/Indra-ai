package ai.indra.desktop;

import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Starts and stops the companion backend jar so the desktop app works even when
 * the user has never opened a terminal.
 *
 * <p>The manager only ever launches the bundled, signed <code>indra-backend-all.jar</code>.
 * It never runs AI-generated commands.</p>
 */
public final class BackendManager {

    private Process process;
    private final DesktopConfig config;

    public BackendManager(DesktopConfig config) {
        this.config = config;
    }

    public static Path jarPath() {
        Path local = Paths.get(System.getProperty("user.dir"), "indra-backend-all.jar");
        if (Files.exists(local)) {
            return local;
        }
        Path sibling = Paths.get(System.getProperty("user.dir")).resolve("../indra-backend/build/libs/indra-backend-all.jar");
        return Files.exists(sibling) ? sibling.normalize() : local;
    }

    public static boolean jarAvailable() {
        return Files.exists(jarPath());
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    /** Attempts a health check against the configured URL. */
    public boolean healthCheck(DesktopApi api) {
        try {
            JsonObject health = api.health();
            return "ok".equalsIgnoreCase(health.getString("status", "ok"));
        } catch (DesktopApi.ApiException error) {
            return false;
        }
    }

    /** Starts the backend in the background, reusing the user's data directory. */
    public String start() {
        if (isRunning()) {
            return "The backend is already running (pid " + process.pid() + ").";
        }
        Path jar = parentJar();
        if (jar == null) {
            return "Could not find indra-backend-all.jar. Build it with: gradlew :indra-backend:shadowJar";
        }
        List<String> command = new ArrayList<String>();
        command.add(javaBinary());
        command.add("-jar");
        command.add(jar.toAbsolutePath().toString());
        command.add("serve");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile().toFile()));
        Path dataDir = DesktopConfig.directory().resolve("backend-data");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException error) {
            Log.warn("BackendManager", "Cannot create data directory: " + error.getMessage());
        }
        builder.environment().put("INDRA_DATA_DIR", dataDir.toAbsolutePath().toString());
        builder.environment().put("INDRA_PORT", port());
        try {
            process = builder.start();
            return "Backend starting on port " + port() + " (log: " + logFile() + ")";
        } catch (IOException error) {
            Log.error("BackendManager", "Could not start backend", error);
            return "Could not start the backend: " + error.getMessage();
        }
    }

    private Path parentJar() {
        Path candidate = jarPath();
        if (Files.exists(candidate)) {
            return candidate;
        }
        File[] files = new File(".").getAbsoluteFile().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith("indra-backend") && file.getName().endsWith(".jar")) {
                    return file.toPath();
                }
            }
        }
        return null;
    }

    public String stop() {
        if (!isRunning()) {
            return "The backend is not running.";
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        process = null;
        return "Backend stopped.";
    }

    private String port() {
        String url = config.getBaseUrl();
        int colon = url.lastIndexOf(':');
        if (colon > 0 && colon < url.length() - 1) {
            String tail = url.substring(colon + 1);
            int slash = tail.indexOf('/');
            String port = slash > 0 ? tail.substring(0, slash) : tail;
            if (port.matches("[0-9]{2,5}")) {
                return port;
            }
        }
        return "8420";
    }

    private Path logFile() {
        return DesktopConfig.directory().resolve("backend.log");
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        if (home == null) {
            return "java";
        }
        File executable = new File(home, "bin" + File.separator + (isWindows() ? "java.exe" : "java"));
        return executable.exists() ? executable.getAbsolutePath() : "java";
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }
}
