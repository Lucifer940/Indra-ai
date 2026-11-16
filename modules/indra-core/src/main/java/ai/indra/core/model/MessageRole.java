package ai.indra.core.model;

/** Author of a chat message. */
public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM;

    public static MessageRole fromName(String name) {
        if (name != null) {
            String normalized = name.trim().toUpperCase(java.util.Locale.ROOT);
            for (MessageRole role : values()) {
                if (role.name().equals(normalized)) {
                    return role;
                }
            }
        }
        return USER;
    }
}
