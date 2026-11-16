package ai.indra.core.security;

import ai.indra.core.util.Strings;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/** Input validation shared by the backend and both clients. */
public final class Validation {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final String[] WEAK_PASSWORDS = {
        "password", "passw0rd", "12345678", "123456789", "1234567890", "qwertyuiop",
        "indra1234", "admin1234", "letmein123", "welcome123"
    };

    private Validation() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String requireEmail(String field, String value) {
        String email = normalizeEmail(value);
        if (Strings.isBlank(email)) {
            throw new ValidationException(field, "Email is required");
        }
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new ValidationException(field, "Enter a valid email address");
        }
        return email;
    }

    /** Password policy: 10-128 chars, at least one letter and one digit. */
    public static String requirePassword(String field, String value) {
        if (value == null || value.isEmpty()) {
            throw new ValidationException(field, "Password is required");
        }
        if (value.length() < 10) {
            throw new ValidationException(field, "Password must be at least 10 characters");
        }
        if (value.length() > 128) {
            throw new ValidationException(field, "Password must be at most 128 characters");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new ValidationException(field, "Password must contain letters and numbers");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String weak : WEAK_PASSWORDS) {
            if (lower.contains(weak)) {
                throw new ValidationException(field, "Password is too common - choose another");
            }
        }
        return value;
    }

    public static String requireText(String field, String value, int min, int max) {
        String text = sanitize(value);
        if (text == null || text.length() < min) {
            throw new ValidationException(field, field + " is required");
        }
        if (text.length() > max) {
            throw new ValidationException(field, field + " must be at most " + max + " characters");
        }
        return text;
    }

    public static String optionalText(String field, String value, int max) {
        String text = sanitize(value);
        if (text != null && text.length() > max) {
            throw new ValidationException(field, field + " must be at most " + max + " characters");
        }
        return text;
    }

    /** True when the value is a well-formed UUID. */
    public static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        return value.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    public static String requireUuid(String field, String value) {
        if (!isUuid(value)) {
            throw new ValidationException(field, "Invalid identifier");
        }
        return value;
    }

    public static String requireOneOf(String field, String value, Collection<String> allowed) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !allowed.contains(normalized)) {
            throw new ValidationException(field, "Unsupported value: " + value);
        }
        return normalized;
    }

    public static long requireRange(String field, long value, long min, long max) {
        if (value < min || value > max) {
            throw new ValidationException(field, "Value must be between " + min + " and " + max);
        }
        return value;
    }

    public static int requireRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new ValidationException(field, "Value must be between " + min + " and " + max);
        }
        return value;
    }

    /** Strips control characters and normalises whitespace without losing meaning. */
    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = CONTROL_CHARS.matcher(value.replace('\u0000', ' ')).replaceAll("");
        cleaned = cleaned.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
