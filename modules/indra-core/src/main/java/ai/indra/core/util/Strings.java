package ai.indra.core.util;

import java.util.ArrayList;
import java.util.List;

/** Null-safe string helpers (no Android or JDK-11-only APIs). */
public final class Strings {

    private Strings() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static String join(String delimiter, List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(delimiter);
            }
            out.append(parts.get(i));
        }
        return out.toString();
    }

    /** Splits on whitespace, dropping empty entries. */
    public static List<String> words(String value) {
        List<String> result = new ArrayList<String>();
        if (value == null) {
            return result;
        }
        for (String part : value.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    public static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    /** Case-insensitive word-boundary containment check. */
    public static boolean containsWord(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return false;
        }
        String text = " " + lower(haystack).replaceAll("[^\\p{L}\\p{N}]+", " ") + " ";
        String word = " " + lower(needle).trim() + " ";
        return text.contains(word);
    }

    /** Masks a secret so it can appear in logs: sk-abcd...wxyz. */
    public static String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "<empty>";
        }
        String trimmed = secret.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    public static String repeat(char c, int count) {
        StringBuilder out = new StringBuilder(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            out.append(c);
        }
        return out.toString();
    }
}
