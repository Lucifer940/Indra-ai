package ai.indra.core.model;

import java.util.Locale;

/** Access role. Privileged actions are always re-checked on the backend. */
public enum Role {
    ADMIN,
    USER;

    public static Role fromName(String name) {
        if (name == null) {
            return USER;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (Role role : values()) {
            if (role.name().equals(normalized)) {
                return role;
            }
        }
        return USER;
    }
}
