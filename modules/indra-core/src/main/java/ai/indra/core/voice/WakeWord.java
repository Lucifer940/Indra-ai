package ai.indra.core.voice;

import ai.indra.core.util.Strings;

/**
 * Wake-word matching for hands-free use. The microphone is only ever opened by a
 * visible foreground service started by the user - never silently.
 */
public final class WakeWord {

    public static final String DEFAULT = "indra";

    private WakeWord() {
    }

    /** Common speech-to-text renderings of "Indra" and its Nepali spelling. */
    private static final String[] VARIANTS = {
        "indra", "indhra", "indara", "indrah", "intra", "indraa", "इन्द्र", "इन्द्रा", "indra ai"
    };

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** True when the transcript contains the wake word anywhere. */
    public static boolean contains(String transcript, String wakeWord) {
        String text = normalize(transcript);
        if (text.isEmpty()) {
            return false;
        }
        String configured = Strings.isBlank(wakeWord) ? DEFAULT : normalize(wakeWord);
        if (text.contains(configured)) {
            return true;
        }
        for (String variant : VARIANTS) {
            if (text.contains(variant)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the command that follows the wake word, e.g. "Indra open WhatsApp"
     * becomes "open whatsapp". Returns null when no wake word is present.
     */
    public static String extractCommand(String transcript, String wakeWord) {
        if (!contains(transcript, wakeWord)) {
            return null;
        }
        String text = transcript == null ? "" : transcript.trim();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String configured = Strings.isBlank(wakeWord) ? DEFAULT : wakeWord.trim().toLowerCase(java.util.Locale.ROOT);
        int index = lower.indexOf(configured);
        if (index < 0) {
            for (String variant : VARIANTS) {
                index = lower.indexOf(variant);
                if (index >= 0) {
                    break;
                }
            }
        }
        if (index < 0) {
            return "";
        }
        String remainder = text.substring(index + configured.length());
        remainder = remainder.replaceAll("^[\\s,.:;!?।-]+", "").trim();
        return remainder;
    }

    /** True when the phrase is only the wake word ("hey indra"). */
    public static boolean isBareWakeWord(String transcript, String wakeWord) {
        String command = extractCommand(transcript, wakeWord);
        return command != null && command.trim().isEmpty();
    }
}
