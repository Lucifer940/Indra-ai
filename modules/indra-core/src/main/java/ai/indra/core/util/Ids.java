package ai.indra.core.util;

import java.security.SecureRandom;
import java.util.UUID;

/** Secure identifier helpers. */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private Ids() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** Uncased-safe random text id (no look-alike characters). */
    public static String random(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return out.toString();
    }

    /** Numeric code of the requested length, e.g. for pairing codes. */
    public static String numericCode(int digits) {
        StringBuilder out = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            out.append((char) ('0' + RANDOM.nextInt(10)));
        }
        return out.toString();
    }
}
