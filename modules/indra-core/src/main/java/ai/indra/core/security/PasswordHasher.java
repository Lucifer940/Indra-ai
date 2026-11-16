package ai.indra.core.security;

/** PBKDF2-HMAC-SHA256 password hashing with per-password salt and versioning. */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final int DEFAULT_ITERATIONS = 210000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    public PasswordHasher(int iterations) {
        if (iterations < 10000) {
            throw new IllegalArgumentException("PBKDF2 iteration count too low");
        }
        this.iterations = iterations;
    }

    /** Produces {@code pbkdf2_sha256$iterations$salt$hash} (base64 parts). */
    public String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        byte[] salt = Crypto.randomBytes(SALT_BYTES);
        byte[] derived = Crypto.pbkdf2(password.toCharArray(), salt, iterations, KEY_BITS);
        return PREFIX + "$" + iterations + "$" + Base64s.encode(salt) + "$" + Base64s.encode(derived);
    }

    public boolean verify(String stored, String password) {
        if (stored == null || password == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        int storedIterations;
        try {
            storedIterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException error) {
            return false;
        }
        byte[] salt = Base64s.decode(parts[2]);
        byte[] expected = Base64s.decode(parts[3]);
        byte[] actual = Crypto.pbkdf2(password.toCharArray(), salt, storedIterations, expected.length * 8);
        return Crypto.constantTimeEquals(expected, actual);
    }

    /** True when the stored hash uses weaker parameters than the current policy. */
    public boolean needsRehash(String stored) {
        if (stored == null) {
            return true;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return true;
        }
        try {
            return Integer.parseInt(parts[1]) < iterations;
        } catch (NumberFormatException error) {
            return true;
        }
    }

    public int getIterations() {
        return iterations;
    }
}
