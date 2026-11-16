package ai.indra.core.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentClassifierTest {

    private static final long NOW = 1794823200000L;
    private static final String ZONE = "UTC";

    private static Intent intentOf(String message) {
        return IntentClassifier.classify(message, NOW, ZONE).getIntent();
    }

    @Test
    void recognisesReminderCommands() {
        IntentResult result = IntentClassifier.classify("Indra remind me tomorrow at 9 am to attend a meeting", NOW, ZONE);
        assertEquals(Intent.REMINDER_CREATE, result.getIntent());
        assertEquals(1794906000000L, result.slotLong("at", 0L));
        assertTrue(result.slot("title").contains("attend"));
        assertTrue(result.getConfidence() > 0.5d);
    }

    @Test
    void reminderWithoutTimeNeedsClarification() {
        IntentResult result = IntentClassifier.classify("remind me to call the bank", NOW, ZONE);
        assertEquals(Intent.REMINDER_CREATE, result.getIntent());
        assertEquals(0L, result.slotLong("at", 0L));
        assertTrue(result.requiresConfirmation());
    }

    @Test
    void recognisesMemoryCommands() {
        assertEquals(Intent.MEMORY_REMEMBER, intentOf("remember that my anniversary is in June"));
        assertEquals(Intent.MEMORY_FORGET, intentOf("forget that I love momo"));
        assertEquals(Intent.MEMORY_LIST, intentOf("what do you remember about me"));
        assertEquals(Intent.MEMORY_CLEAR, intentOf("clear all memories"));
    }

    @Test
    void recognisesDeviceActionsWithCorrectRisk() {
        assertEquals(Intent.CALL, intentOf("call +9779800000000"));
        assertEquals(Intent.SEND_SMS, intentOf("send sms to Ram saying hello"));
        assertEquals(Intent.WHATSAPP, intentOf("send whatsapp to Sita"));
        assertEquals(Intent.OPEN_APP, intentOf("open app youtube"));
        assertEquals(Intent.OPEN_URL, intentOf("open website https://example.com"));

        IntentResult call = IntentClassifier.classify("call +9779800000000", NOW, ZONE);
        assertEquals(RiskLevel.HIGH, call.getRisk());
        assertTrue(call.requiresConfirmation());
        assertEquals("+9779800000000", call.slot("recipient"));
    }

    @Test
    void recognisesInformationRequests() {
        assertEquals(Intent.WEATHER, intentOf("what's the weather in Kathmandu"));
        assertEquals(Intent.NEWS, intentOf("show me today's news"));
        assertEquals(Intent.WEB_SEARCH, intentOf("search google for best laptops 2026"));
        assertEquals(Intent.TIME_NOW, intentOf("what time is it"));
        assertEquals(Intent.SYSTEM_INFO, intentOf("show my battery status"));
    }

    @Test
    void recognisesControlCommands() {
        assertEquals(Intent.VOICE_STOP, intentOf("stop talking"));
        assertEquals(Intent.ASSISTANT_OFF, intentOf("go to sleep"));
        assertEquals(Intent.PERSONALITY_SET, intentOf("change personality to funnier"));
        assertEquals(Intent.BRAIN_SET, intentOf("switch to jarvis brain"));
        assertEquals(Intent.LANGUAGE_SET, intentOf("speak nepali please"));
    }

    @Test
    void recognisesNepaliCommands() {
        assertEquals(Intent.REMINDER_CREATE, intentOf("भोलि बिहान ९ बजे सम्झाउनु मिटिङ छ"));
        assertEquals(Intent.WEATHER, intentOf("आज मौसम कस्तो छ"));
    }

    @Test
    void unknownInputFallsBackToChat() {
        IntentResult result = IntentClassifier.classify("explain quantum entanglement simply", NOW, ZONE);
        assertEquals(Intent.CHAT, result.getIntent());
        assertFalse(result.requiresConfirmation());
    }

    @Test
    void personaAndBrainSlotsAreExtracted() {
        assertEquals("funny", IntentClassifier.classify("be funny now", NOW, ZONE).slot("persona"));
        assertEquals("tactical", IntentClassifier.classify("switch to edith brain", NOW, ZONE).slot("brain"));
        assertEquals("ne", IntentClassifier.classify("speak nepali", NOW, ZONE).slot("language"));
    }
}
