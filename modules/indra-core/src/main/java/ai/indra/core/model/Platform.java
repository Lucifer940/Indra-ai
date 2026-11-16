package ai.indra.core.model;

/** Client platform identifiers used for device pairing and sync. */
public enum Platform {
    ANDROID,
    WINDOWS,
    WEB,
    BACKEND;

    public static Platform fromName(String name) {
        if (name == null) {
            return ANDROID;
        }
        String normalized = name.trim().toUpperCase(java.util.Locale.ROOT);
        for (Platform platform : values()) {
            if (platform.name().equals(normalized)) {
                return platform;
            }
        }
        return ANDROID;
    }
}
