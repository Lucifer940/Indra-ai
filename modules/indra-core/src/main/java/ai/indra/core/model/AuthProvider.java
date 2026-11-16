package ai.indra.core.model;

/** Identity source for an account. */
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    GITHUB;

    public static AuthProvider fromName(String name) {
        if (name != null) {
            String normalized = name.trim().toUpperCase(java.util.Locale.ROOT);
            for (AuthProvider provider : values()) {
                if (provider.name().equals(normalized)) {
                    return provider;
                }
            }
        }
        return LOCAL;
    }
}
