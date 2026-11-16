package ai.indra.core.model;

import ai.indra.core.util.Strings;

/** Conversation / speech languages supported by INDRA AI. */
public enum Language {
    ENGLISH("en", "English", "english"),
    NEPALI("ne", "नेपाली", "nepali");

    private final String code;
    private final String label;
    private final String englishName;

    Language(String code, String label, String englishName) {
        this.code = code;
        this.label = label;
        this.englishName = englishName;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getEnglishName() {
        return englishName;
    }

    public static Language fromCode(String code) {
        if (Strings.isBlank(code)) {
            return ENGLISH;
        }
        String normalized = code.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("ne") || normalized.startsWith("np")) {
            return NEPALI;
        }
        return ENGLISH;
    }
}
