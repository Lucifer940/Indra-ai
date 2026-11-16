package ai.indra.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Personality modes. INDRA AI is written as a female assistant; each persona
 * changes tone, sentence rhythm and emotional warmth while keeping the same
 * identity and facts. Prompt text lives in
 * {@link ai.indra.core.ai.PersonalityEngine}.
 */
public enum Persona {
    FRIENDLY("friendly", "Friendly", "Warm everyday companion who chats naturally"),
    PROFESSIONAL("professional", "Professional", "Precise, business-like and efficient"),
    FUNNY("funny", "Funny", "Light-hearted with playful humour"),
    STUDY("study", "Study Coach", "Patient teacher with step-by-step explanations"),
    MOTIVATIONAL("motivational", "Motivational", "Encouraging coach who pushes you forward"),
    DRAMATIC("dramatic", "Dramatic", "Theatrical storytelling with big energy"),
    COMPANION("companion", "Companion", "Gentle, affectionate and emotionally supportive");

    private final String id;
    private final String label;
    private final String description;

    Persona(String id, String label, String description) {
        this.id = id;
        this.label = label;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public static Persona fromId(String id) {
        if (id != null) {
            String normalized = id.trim().toLowerCase(java.util.Locale.ROOT);
            for (Persona persona : values()) {
                if (persona.id.equals(normalized) || persona.name().equalsIgnoreCase(normalized)) {
                    return persona;
                }
            }
        }
        return FRIENDLY;
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<String>();
        for (Persona persona : values()) {
            ids.add(persona.id);
        }
        return ids;
    }
}
