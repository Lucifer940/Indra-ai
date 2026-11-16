package ai.indra.core.model;

/** Groupings shown on the memory management screen. */
public enum MemoryCategory {
    PROFILE,
    PREFERENCE,
    RELATIONSHIP,
    ROUTINE,
    GOAL,
    WORK,
    STUDY,
    HEALTH,
    CONTACT,
    OTHER;

    public static MemoryCategory fromName(String name) {
        if (name == null) {
            return OTHER;
        }
        String normalized = name.trim().toUpperCase(java.util.Locale.ROOT);
        for (MemoryCategory category : values()) {
            if (category.name().equals(normalized)) {
                return category;
            }
        }
        return OTHER;
    }
}
