package ai.indra.core.license;

import ai.indra.core.security.Crypto;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Licence key generation and format validation.
 *
 * <p>Keys look like {@code INDRA-7K4M-2XQ9-8LRT-B3NC}. The leading 12 characters
 * are random and the first character of the last group is a checksum, which
 * catches typos. Real authority always belongs to the backend, which stores the
 * issued key and its binding - never the client.</p>
 */
public final class LicenseKeys {

    public static final String PREFIX = "INDRA";
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private LicenseKeys() {
    }

    public static String generate() {
        StringBuilder body = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            body.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        char checksum = checksum(body.toString());
        StringBuilder last = new StringBuilder(4);
        last.append(checksum);
        for (int i = 0; i < 3; i++) {
            last.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return PREFIX + "-" + body.substring(0, 4) + "-" + body.substring(4, 8) + "-"
                + body.substring(8, 12) + "-" + last;
    }

    /** Upper-cases, strips spaces and normalises separators. */
    public static String normalize(String key) {
        if (key == null) {
            return "";
        }
        String cleaned = key.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (cleaned.startsWith(PREFIX) && cleaned.length() >= PREFIX.length() + 12) {
            String body = cleaned.substring(PREFIX.length());
            StringBuilder out = new StringBuilder(PREFIX);
            for (int i = 0; i < body.length(); i++) {
                if (i % 4 == 0) {
                    out.append('-');
                }
                out.append(body.charAt(i));
            }
            return out.toString();
        }
        return cleaned;
    }

    public static boolean isValidFormat(String key) {
        String normalized = normalize(key);
        String[] parts = normalized.split("-");
        if (parts.length != 5 || !PREFIX.equals(parts[0])) {
            return false;
        }
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].length() != 4) {
                return false;
            }
            for (int j = 0; j < parts[i].length(); j++) {
                if (indexOf(parts[i].charAt(j)) < 0) {
                    return false;
                }
            }
        }
        String body = parts[1] + parts[2] + parts[3];
        return checksum(body) == parts[4].charAt(0);
    }

    /** Shows only the tail so a licence can be displayed safely in the UI. */
    public static String mask(String key) {
        String normalized = normalize(key);
        if (normalized.length() <= 10) {
            return "INDRA-****";
        }
        return "INDRA-****-****-" + normalized.substring(normalized.length() - 9);
    }

    /** Stable fingerprint used for logging without leaking the key. */
    public static String fingerprint(String key) {
        return Crypto.sha256Hex(normalize(key)).substring(0, 12);
    }

    private static char checksum(String body) {
        long sum = 0;
        for (int i = 0; i < body.length(); i++) {
            int index = Math.max(0, indexOf(body.charAt(i)));
            sum += (long) index * (i + 3);
        }
        return ALPHABET[(int) (sum % ALPHABET.length)];
    }

    private static int indexOf(char c) {
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] == c) {
                return i;
            }
        }
        return -1;
    }
}
