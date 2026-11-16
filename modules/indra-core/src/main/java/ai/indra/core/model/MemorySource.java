package ai.indra.core.model;

/** Where a memory came from - shown in the UI so users can audit it. */
public enum MemorySource {
    USER_STATED,
    AUTO_APPROVED,
    IMPORTED,
    BACKEND_SYNC;

    public static MemorySource fromName(String name) {
        if (name != null) {
            String normalized = name.trim().toUpperCase(java.util.Locale.ROOT);
            for (MemorySource source : values()) {
                if (source.name().equals(normalized)) {
                    return source;
                }
            }
        }
        return USER_STATED;
    }
}
