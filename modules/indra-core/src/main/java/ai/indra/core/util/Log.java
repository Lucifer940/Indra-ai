package ai.indra.core.util;

/**
 * Tiny structured logger that never prints secrets. API keys, bearer tokens and
 * password-like fields are redacted before they reach stdout/stderr.
 */
public final class Log {

    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    private static volatile Level minimum = Level.INFO;
    private static volatile boolean includeTimestamps = true;

    private Log() {
    }

    public static void setMinimum(Level level) {
        if (level != null) {
            minimum = level;
        }
    }

    public static void setIncludeTimestamps(boolean include) {
        includeTimestamps = include;
    }

    public static void debug(String tag, String message) {
        log(Level.DEBUG, tag, message, null);
    }

    public static void info(String tag, String message) {
        log(Level.INFO, tag, message, null);
    }

    public static void warn(String tag, String message) {
        log(Level.WARN, tag, message, null);
    }

    public static void error(String tag, String message) {
        log(Level.ERROR, tag, message, null);
    }

    public static void error(String tag, String message, Throwable error) {
        log(Level.ERROR, tag, message, error);
    }

    public static void log(Level level, String tag, String message, Throwable error) {
        if (level.ordinal() < minimum.ordinal()) {
            return;
        }
        StringBuilder line = new StringBuilder(96);
        if (includeTimestamps) {
            line.append(Times.toIso(System.currentTimeMillis())).append(' ');
        }
        line.append('[').append(level.name()).append("] ").append(tag).append(": ").append(redact(message));
        if (error != null) {
            line.append(" | ").append(error.getClass().getSimpleName()).append(": ").append(redact(error.getMessage()));
        }
        if (level == Level.ERROR || level == Level.WARN) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }
    }

    /** Removes credential-looking substrings from a message. */
    public static String redact(String message) {
        if (message == null) {
            return "null";
        }
        String result = message;
        result = result.replaceAll("(?i)(sk|pk|api|key|token|secret|password)[-_ ]?[A-Za-z0-9]*\\s*[=:]\\s*[^\\s,;]+", "$1=***");
        result = result.replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "Bearer ***");
        result = result.replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "sk-***");
        return result;
    }
}
