package ai.indra.core.model;

/** Repetition rule for reminders. Kept intentionally small and explicit. */
public enum Recurrence {
    NONE("none", "Once"),
    DAILY("daily", "Every day"),
    WEEKDAYS("weekdays", "Monday to Friday"),
    WEEKLY("weekly", "Every week"),
    MONTHLY("monthly", "Every month"),
    YEARLY("yearly", "Every year");

    private final String id;
    private final String label;

    Recurrence(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public boolean repeats() {
        return this != NONE;
    }

    public static Recurrence fromId(String id) {
        if (id != null) {
            String normalized = id.trim().toLowerCase(java.util.Locale.ROOT);
            for (Recurrence rule : values()) {
                if (rule.id.equals(normalized) || rule.name().equalsIgnoreCase(normalized)) {
                    return rule;
                }
            }
        }
        return NONE;
    }
}
