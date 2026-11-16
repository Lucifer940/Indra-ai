package ai.indra.core.voice;

import ai.indra.core.model.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceTest {

    @Test
    void wakeWordDetectedInVariousTranscripts() {
        assertTrue(WakeWord.contains("Hey Indra, what's the weather?", "indra"));
        assertTrue(WakeWord.contains("इन्द्र मौसम कस्तो छ", "indra"));
        assertTrue(WakeWord.contains("indra open youtube", "indra"));
        assertFalse(WakeWord.contains("what is the weather", "indra"));
    }

    @Test
    void commandIsExtractedAfterWakeWord() {
        assertEquals("open youtube", WakeWord.extractCommand("Indra open YouTube", "indra"));
        assertEquals("what's the weather", WakeWord.extractCommand("hey indra, what's the weather", "indra"));
        assertNull(WakeWord.extractCommand("hello there", "indra"));
        assertTrue(WakeWord.isBareWakeWord("hey indra", "indra"));
    }

    @Test
    void speechSanitizerRemovesMarkdownAndEmoji() {
        String spoken = SpeechSanitizer.forSpeech("**Bold** answer with `code`, a [link](https://x.y) and 50% chance 🎉");
        assertFalse(spoken.contains("**"));
        assertFalse(spoken.contains("`"));
        assertFalse(spoken.contains("https://"));
        assertFalse(spoken.contains("🎉"));
        assertTrue(spoken.contains("50 percent"));
    }

    @Test
    void speechSanitizerChunksLongText() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            text.append("This is sentence number ").append(i).append(". ");
        }
        List<String> chunks = SpeechSanitizer.chunk(text.toString(), 120);
        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 200);
        }
    }

    @Test
    void languageDetectionHandlesBothScripts() {
        assertEquals(Language.NEPALI, TextLanguageDetector.detect("नमस्ते, म इन्द्र हुँ।"));
        assertEquals(Language.ENGLISH, TextLanguageDetector.detect("Hello, I am Indra."));
        assertEquals(Language.ENGLISH, TextLanguageDetector.detect(""));
        assertEquals("ne-NP", TextLanguageDetector.toBcp47(Language.NEPALI));
        assertEquals("en-US", TextLanguageDetector.toBcp47(Language.ENGLISH));
    }

    @Test
    void localVoiceCommandsAreRecognised() {
        assertEquals(VoiceCommand.STOP_SPEAKING, VoiceCommand.detect("Indra stop talking"));
        assertEquals(VoiceCommand.STOP_SPEAKING, VoiceCommand.detect("चुप लाग्नु"));
        assertEquals(VoiceCommand.TURN_OFF, VoiceCommand.detect("turn off indra"));
        assertEquals(VoiceCommand.WAKE, VoiceCommand.detect("hey indra"));
        assertEquals(VoiceCommand.NONE, VoiceCommand.detect("write a poem about mountains"));
    }
}
