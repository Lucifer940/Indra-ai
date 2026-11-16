package ai.indra.core.tasks;

import ai.indra.core.model.Recurrence;

import java.util.Calendar;
import java.util.TimeZone;

/** Computes the next occurrence of a repeating reminder. */
public final class RecurrenceEngine {

    private RecurrenceEngine() {
    }

    public static String describe(Recurrence rule) {
        if (rule == null) {
            return "Once";
        }
        switch (rule) {
            case DAILY:
                return "every day";
            case WEEKDAYS:
                return "every weekday";
            case WEEKLY:
                return "every week";
            case MONTHLY:
                return "every month";
            case YEARLY:
                return "every year";
            case NONE:
            default:
                return "once";
        }
    }

    /**
     * Next fire time strictly after {@code fromMillis}, preserving the original
     * time of day in the user's zone. Returns 0 for non-repeating reminders.
     */
    public static long nextOccurrence(long fromMillis, Recurrence rule, String timeZoneId) {
        if (rule == null || rule == Recurrence.NONE || fromMillis <= 0L) {
            return 0L;
        }
        TimeZone zone = TimeZone.getTimeZone(timeZoneId == null ? "UTC" : timeZoneId);
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(fromMillis);
        switch (rule) {
            case DAILY:
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case WEEKDAYS:
                do {
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                } while (isWeekend(calendar));
                break;
            case WEEKLY:
                calendar.add(Calendar.DAY_OF_MONTH, 7);
                break;
            case MONTHLY:
                calendar.add(Calendar.MONTH, 1);
                break;
            case YEARLY:
                calendar.add(Calendar.YEAR, 1);
                break;
            case NONE:
            default:
                return 0L;
        }
        return calendar.getTimeInMillis();
    }

    private static boolean isWeekend(Calendar calendar) {
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY;
    }

    /** Human sentence for a reminder acknowledgement. */
    public static String acknowledgement(String title, long remindAt, Recurrence rule, String timeZoneId) {
        StringBuilder text = new StringBuilder("Got it. ");
        text.append(rule != null && rule.repeats() ? "Repeating reminder" : "Reminder").append(" set");
        if (remindAt > 0L) {
            text.append(" for ").append(ai.indra.core.util.Times.toDisplay(remindAt, timeZoneId));
        }
        text.append(": ").append(title);
        if (rule != null && rule.repeats()) {
            text.append(" (").append(describe(rule)).append(")");
        }
        text.append('.');
        return text.toString();
    }
}
