package ai.indra.core.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalTimeParserTest {

    /** Monday 16 November 2026, 10:00 UTC. */
    private static final long NOW = 1794823200000L;
    private static final String ZONE = "UTC";

    private static long parse(String text) {
        NaturalTimeParser.Result result = NaturalTimeParser.parse(text, NOW, ZONE);
        assertTrue(result.isFound(), "expected to parse: " + text);
        return result.getMillis();
    }

    @Test
    void parsesRelativeOffsets() {
        assertEquals(1794830400000L, parse("remind me in 2 hours to call mom"));
        assertEquals(NOW + 30L * 60L * 1000L, parse("ping me in 30 minutes"));
        assertEquals(NOW + 15L * 1000L, parse("in 15 seconds"));
        assertEquals(NOW + 3L * 24L * 60L * 60L * 1000L, parse("3 days later take medicine"));
    }

    @Test
    void parsesTomorrowWithClockTime() {
        assertEquals(1794906000000L, parse("remind me tomorrow at 9 am to attend a meeting"));
        assertEquals(1794906000000L, parse("tomorrow 9am meeting"));
    }

    @Test
    void parsesTodayEveningTime() {
        assertEquals(1794862800000L, parse("today at 21:00 dinner"));
        assertEquals(1794862800000L, parse("today at 9 pm dinner"));
    }

    @Test
    void parsesWeekdays() {
        assertEquals(1795186800000L, parse("next friday at 3 pm submit report"));
        assertEquals(1795186800000L, parse("friday 15:00 submit report"));
    }

    @Test
    void parsesAbsoluteDates() {
        assertEquals(1801299600000L, parse("on 30 january 2027 exam"));
        assertEquals(1801299600000L, parse("on 2027-01-30 exam"));
    }

    @Test
    void parsesNepaliPhrases() {
        // भोलि बिहान ९ बजे -> tomorrow 09:00
        assertEquals(1794906000000L, parse("भोलि बिहान ९ बजे मिटिङ छ"));
        // २ घण्टा पछि -> in two hours
        assertEquals(NOW + 2L * 60L * 60L * 1000L, parse("२ घण्टा पछि सम्झाउनु"));
    }

    @Test
    void returnsNotFoundForPlainChat() {
        assertFalse(NaturalTimeParser.parse("what is photosynthesis", NOW, ZONE).isFound());
        assertFalse(NaturalTimeParser.parse("", NOW, ZONE).isFound());
    }

    @Test
    void barePastClockTimeRollsToTomorrow() {
        // 08:00 is before 10:00, so it should schedule tomorrow 08:00 UTC.
        assertEquals(1794902400000L, parse("remind me at 8 am workout"));
    }

    @Test
    void extractsReminderTitle() {
        assertEquals("attend meeting", NaturalTimeParser.extractTitle("remind me tomorrow at 9 am to attend meeting"));
        assertEquals("call mom", NaturalTimeParser.extractTitle("Hey Indra, remind me to call mom in 2 hours"));
        assertFalse(NaturalTimeParser.extractTitle("remind me to buy milk").isEmpty());
    }

    @Test
    void normalisesDevanagariDigits() {
        assertEquals("2026 9", NaturalTimeParser.normalize("२०२६ ९"));
    }

    @Test
    void recurrenceEngineAdvancesCorrectly() {
        assertEquals(NOW + 24L * 60L * 60L * 1000L,
                RecurrenceEngine.nextOccurrence(NOW, ai.indra.core.model.Recurrence.DAILY, ZONE));
        assertEquals(NOW + 7L * 24L * 60L * 60L * 1000L,
                RecurrenceEngine.nextOccurrence(NOW, ai.indra.core.model.Recurrence.WEEKLY, ZONE));
        assertEquals(0L, RecurrenceEngine.nextOccurrence(NOW, ai.indra.core.model.Recurrence.NONE, ZONE));
        long nextWeekday = RecurrenceEngine.nextOccurrence(NOW, ai.indra.core.model.Recurrence.WEEKDAYS, ZONE);
        assertTrue(nextWeekday > NOW);
    }
}
