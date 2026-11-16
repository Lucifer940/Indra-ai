package ai.indra.core.voice;

import ai.indra.core.model.Language;

/** Cheap, deterministic language check used to pick TTS voice and prompts. */
public final class TextLanguageDetector {

    private TextLanguageDetector() {
    }

    public static Language detect(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Language.ENGLISH;
        }
        int devanagari = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0900' && c <= '\u097F') {
                devanagari++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latin++;
            }
        }
        if (devanagari > 0 && devanagari * 3 >= latin) {
            return Language.NEPALI;
        }
        return Language.ENGLISH;
    }

    /** BCP-47 tags used by Android TextToSpeech and Windows SAPI. */
    public static String toBcp47(Language language) {
        return language == Language.NEPALI ? "ne-NP" : "en-US";
    }
}
