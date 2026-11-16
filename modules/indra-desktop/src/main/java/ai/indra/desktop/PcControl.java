package ai.indra.desktop;

import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Windows PC control with a strict allowlist.
 *
 * <p>Rules enforced here, not in the UI:</p>
 * <ul>
 *   <li>Only known application names can be launched - never free-form commands.</li>
 *   <li>File operations are limited to the user's own home directory and blocked
 *       for credential/system folders.</li>
 *   <li>Destructive operations (delete, overwrite, move) must be confirmed by the
 *       caller; the AI can never trigger them on its own.</li>
 * </ul>
 */
public final class PcControl {

    /** Result of a PC action. */
    public static final class Result {
        public final boolean success;
        public final String message;

        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /** App allowlist: spoken name -> executable. */
    private static final String[][] APPS = {
            {"notepad", "notepad.exe"},
            {"calculator", "calc.exe"},
            {"calc", "calc.exe"},
            {"paint", "mspaint.exe"},
            {"explorer", "explorer.exe"},
            {"file explorer", "explorer.exe"},
            {"task manager", "taskmgr.exe"},
            {"settings", "ms-settings:"},
            {"control panel", "control.exe"},
            {"word", "winword.exe"},
            {"excel", "excel.exe"},
            {"powerpoint", "powerpnt.exe"},
            {"chrome", "chrome.exe"},
            {"edge", "msedge.exe"},
            {"firefox", "firefox.exe"},
            {"spotify", "spotify.exe"},
            {"vscode", "code.cmd"},
            {"vs code", "code.cmd"},
            {"visual studio code", "code.cmd"},
            {"command prompt", "cmd.exe"},
            {"terminal", "wt.exe"},
            {"snipping tool", "snippingtool.exe"},
            {"photos", "ms-photos:"},
            {"camera", "microsoft.windows.camera:"}
    };

    private static final List<String> BLOCKED_FRAGMENTS = Arrays.asList(
            "\\windows\\system32", "\\windows\\syswow64", "appdata\\roaming\\microsoft\\crypto",
            "appdata\\local\\microsoft\\credentials", "\\.ssh", "\\.gnupg", "\\ntuser",
            "\\desktop.ini", "appdata\\roaming\\microsoft\\protect", "appdata\\roaming\\microsoft\\vault",
            "\\program files", "\\programdata");

    private PcControl() {
    }

    public static boolean isWindows() {
        return BackendManager.isWindows();
    }

    private static Path home() {
        return Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    /** Normalises and validates a path against the allowlist. */
    private static Path safePath(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String cleaned = raw.trim().replace("\"", "");
        Path path = Paths.get(cleaned);
        if (!path.isAbsolute()) {
            path = home().resolve(cleaned);
        }
        path = path.toAbsolutePath().normalize();
        if (!path.startsWith(home())) {
            return null;
        }
        String lower = path.toString().toLowerCase(Locale.ROOT);
        for (String blocked : BLOCKED_FRAGMENTS) {
            if (lower.contains(blocked)) {
                return null;
            }
        }
        return path;
    }

    private static Path knownFolder(String name) {
        Path home = home();
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (key.contains("desktop")) {
            return home.resolve("Desktop");
        }
        if (key.contains("download")) {
            return home.resolve("Downloads");
        }
        if (key.contains("document")) {
            return home.resolve("Documents");
        }
        if (key.contains("picture") || key.contains("photo")) {
            return home.resolve("Pictures");
        }
        if (key.contains("music")) {
            return home.resolve("Music");
        }
        if (key.contains("video")) {
            return home.resolve("Videos");
        }
        return home;
    }

    // --------------------------------------------------------------- apps/urls

    public static Result openApp(String spokenName) {
        String needle = spokenName == null ? "" : spokenName.toLowerCase(Locale.ROOT).trim();
        String target = null;
        String label = null;
        for (String[] entry : APPS) {
            if (needle.equals(entry[0]) || needle.contains(entry[0])) {
                target = entry[1];
                label = entry[0];
                break;
            }
        }
        if (target == null) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < APPS.length; i++) {
                names.append(i == 0 ? "" : ", ").append(APPS[i][0]);
            }
            return new Result(false, "I can only open apps on my allowlist: " + names);
        }
        try {
            if (target.endsWith(":")) {
                Desktop.getDesktop().browse(java.net.URI.create(target));
            } else if (target.endsWith(".cmd") || target.endsWith(".exe")) {
                new ProcessBuilder(target).start();
            } else {
                Desktop.getDesktop().open(new File(target));
            }
            return new Result(true, "Opened " + label);
        } catch (IOException | RuntimeException error) {
            Log.warn("PcControl", "openApp failed: " + error.getMessage());
            return new Result(false, "I couldn't open " + label + " - is it installed?");
        }
    }

    public static JsonArray allowedApps() {
        JsonArray array = new JsonArray();
        for (String[] entry : APPS) {
            array.add(entry[0]);
        }
        return array;
    }

    public static Result openUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return new Result(false, "No address given");
        }
        String target = url.trim();
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "https://" + target;
        }
        if (!target.startsWith("https://") && !target.startsWith("http://")) {
            return new Result(false, "Only web addresses can be opened");
        }
        try {
            Desktop.getDesktop().browse(java.net.URI.create(target));
            return new Result(true, "Opening " + target);
        } catch (IOException | RuntimeException error) {
            return new Result(false, "No browser could open that address");
        }
    }

    public static Result openFolder(String spokenFolder) {
        Path folder = knownFolder(spokenFolder);
        try {
            Desktop.getDesktop().open(folder.toFile());
            return new Result(true, "Opened " + folder);
        } catch (IOException | RuntimeException error) {
            return new Result(false, "I couldn't open " + folder);
        }
    }

    public static Result openPath(String raw) {
        Path path = safePath(raw);
        if (path == null) {
            return new Result(false, "That location is outside your home folder or is protected");
        }
        if (!Files.exists(path)) {
            return new Result(false, "That file doesn't exist");
        }
        try {
            Desktop.getDesktop().open(path.toFile());
            return new Result(true, "Opened " + path.getFileName());
        } catch (IOException | RuntimeException error) {
            return new Result(false, "This file type has no associated app");
        }
    }

    // -------------------------------------------------------------- files

    public static JsonArray searchFiles(String query, String folderHint, int limit) {
        JsonArray results = new JsonArray();
        if (query == null || query.trim().length() < 2) {
            return results;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        Path root = knownFolder(folderHint == null ? "" : folderHint);
        List<Path> queue = new ArrayList<Path>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && results.size() < limit && visited < 4000) {
            Path current = queue.remove(0);
            visited++;
            File[] children = current.toFile().listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                String lower = child.getName().toLowerCase(Locale.ROOT);
                if (lower.contains(needle) && results.size() < limit) {
                    results.add(new JsonObject()
                            .put("name", child.getName())
                            .put("path", child.getAbsolutePath())
                            .put("directory", Boolean.valueOf(child.isDirectory()))
                            .put("size", Long.valueOf(child.length())));
                }
                if (child.isDirectory() && !child.isHidden() && visited < 4000) {
                    queue.add(child.toPath());
                }
            }
        }
        return results;
    }

    public static Result createFolder(String folderHint, String name) {
        if (name == null || name.trim().isEmpty() || name.contains("..")) {
            return new Result(false, "That folder name isn't allowed");
        }
        Path target = knownFolder(folderHint).resolve(name.trim());
        try {
            Files.createDirectories(target);
            return new Result(true, "Created " + target);
        } catch (IOException error) {
            return new Result(false, "Could not create the folder: " + error.getMessage());
        }
    }

    public static Result createTextFile(String rawPath, String content) {
        Path target = safePath(rawPath);
        if (target == null) {
            return new Result(false, "That location isn't allowed");
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return new Result(true, "Saved " + target.getFileName());
        } catch (IOException error) {
            return new Result(false, "Write failed: " + error.getMessage());
        }
    }

    public static Result rename(String rawPath, String newName) {
        Path source = safePath(rawPath);
        if (source == null || newName == null || newName.trim().isEmpty() || newName.contains("..")) {
            return new Result(false, "Rename refused - invalid path or name");
        }
        if (!Files.exists(source)) {
            return new Result(false, "That file doesn't exist");
        }
        Path target = source.resolveSibling(newName.trim());
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return new Result(true, "Renamed to " + target.getFileName());
        } catch (IOException error) {
            return new Result(false, "Rename failed: " + error.getMessage());
        }
    }

    public static Result copy(String rawSource, String rawTargetFolder) {
        Path source = safePath(rawSource);
        Path target = safePath(rawTargetFolder);
        if (source == null || target == null || !Files.exists(source)) {
            return new Result(false, "Copy refused - check the source and destination");
        }
        try {
            Files.createDirectories(target);
            Path destination = target.resolve(source.getFileName());
            if (Files.isDirectory(source)) {
                copyTree(source, destination);
            } else {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Result(true, "Copied to " + destination);
        } catch (IOException error) {
            return new Result(false, "Copy failed: " + error.getMessage());
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.createDirectories(destination);
        File[] children = source.toFile().listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            Path childTarget = destination.resolve(child.getName());
            if (child.isDirectory()) {
                copyTree(child.toPath(), childTarget);
            } else {
                Files.copy(child.toPath(), childTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static Result move(String rawSource, String rawTargetFolder) {
        Path source = safePath(rawSource);
        Path target = safePath(rawTargetFolder);
        if (source == null || target == null || !Files.exists(source)) {
            return new Result(false, "Move refused - check the source and destination");
        }
        try {
            Files.createDirectories(target);
            Path destination = target.resolve(source.getFileName());
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return new Result(true, "Moved to " + destination);
        } catch (IOException error) {
            return new Result(false, "Move failed: " + error.getMessage());
        }
    }

    /** Deletes a file or empty folder. Always requires explicit confirmation upstream. */
    public static Result delete(String rawPath) {
        Path path = safePath(rawPath);
        if (path == null || !Files.exists(path)) {
            return new Result(false, "Nothing to delete at that location");
        }
        try {
            deleteTree(path);
            return new Result(true, "Deleted " + path.getFileName());
        } catch (IOException error) {
            return new Result(false, "Delete failed: " + error.getMessage());
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            File[] children = path.toFile().listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child.toPath());
                }
            }
        }
        Files.deleteIfExists(path);
    }

    // -------------------------------------------------------------- system

    public static JsonObject systemInfo() {
        JsonObject info = new JsonObject();
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        info.put("arch", System.getProperty("os.arch"));
        info.put("java", System.getProperty("java.version"));
        Runtime runtime = Runtime.getRuntime();
        long maxMb = runtime.maxMemory() / (1024L * 1024L);
        long freeMb = runtime.freeMemory() / (1024L * 1024L);
        info.put("javaMaxRamMb", Long.valueOf(maxMb));
        info.put("javaFreeRamMb", Long.valueOf(freeMb));

        try {
            File store = new File(System.getProperty("user.home"));
            long total = store.getTotalSpace() / (1024L * 1024L * 1024L);
            long free = store.getUsableSpace() / (1024L * 1024L * 1024L);
            info.put("diskTotalGb", Long.valueOf(total));
            info.put("diskFreeGb", Long.valueOf(free));
        } catch (RuntimeException error) {
            Log.warn("PcControl", "Disk info unavailable: " + error.getMessage());
        }

        String battery = batteryPercent();
        if (battery != null) {
            info.put("battery", battery);
        }
        return info;
    }

    /** Reads the battery level on Windows through a read-only CIM query. */
    public static String batteryPercent() {
        if (!isWindows()) {
            return null;
        }
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                    "-Command", "(Get-CimInstance Win32_Battery).EstimatedChargeRemaining")
                    .redirectErrorStream(true).start();
            process.waitFor(4, TimeUnit.SECONDS);
            String output = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8).trim();
            if (output.matches("[0-9]{1,3}")) {
                return output + "%";
            }
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.debug("PcControl", "Battery query unavailable: " + error.getMessage());
        }
        return null;
    }

    private static byte[] readAll(java.io.InputStream input) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        input.close();
        return buffer.toByteArray();
    }

    public static Result lockWorkstation() {
        if (!isWindows()) {
            return new Result(false, "Locking is only supported on Windows");
        }
        try {
            new ProcessBuilder("rundll32.exe", "user32.dll,LockWorkStation").start();
            return new Result(true, "Workstation locked");
        } catch (IOException error) {
            return new Result(false, "Could not lock the workstation");
        }
    }
}
