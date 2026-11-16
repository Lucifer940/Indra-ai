package ai.indra.core.voice;

import ai.indra.core.util.Strings;

/** Local voice commands that must work instantly, even offline. */
public enum VoiceCommand {
    STOP_SPEAKING,
    CANCEL,
    TURN_OFF,
    WAKE,
    NONE;

    public static VoiceCommand detect(String transcript) {
        if (Strings.isBlank(transcript)) {
            return NONE;
        }
        String text = Strings.lower(transcript).trim();
        if (text.matches(".*\\b(stop|quiet|silence|chup|shut up|cancel speaking)\\b.*")
                || text.contains("चुप") || text.contains("बन्द गर")) {
            return STOP_SPEAKING;
        }
        if (text.matches(".*\\b(cancel|never mind|nevermind|forget it)\\b.*") || text.contains("रद्द")) {
            return CANCEL;
        }
        if (text.matches(".*\\b(turn off|go to sleep|shut down|sleep now)\\b.*") || text.contains("बन्द होऊ")) {
            return TURN_OFF;
        }
        if (text.trim().matches("^(hey\\s+)?(indra|इन्द्र)[\\s!.,]*$")) {
            return WAKE;
        }
        return NONE;
    }
}
