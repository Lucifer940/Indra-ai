package ai.indra.core.sync;

import ai.indra.core.security.Crypto;

import java.security.SecureRandom;

/** Short, human-friendly pairing codes for phone ↔ PC linking. */
public final class PairingCodes {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private PairingCodes() {
    }

    /** Eight characters grouped as XXXX-XXXX. */
    public static String generate() {
        StringBuilder out = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                out.append('-');
            }
            out.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return out.toString();
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** Salted hash stored server-side; the plain code is shown only once. */
    public static String hash(String code, String salt) {
        if (salt == null || salt.length() < 16) {
            throw new IllegalArgumentException("Pairing salt too short");
        }
        return Crypto.hmacSha256Hex(salt, "pairing:" + normalize(code));
    }

    public static boolean matches(String code, String salt, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        return Crypto.constantTimeEquals(hash(code, salt), storedHash);
    }
}
