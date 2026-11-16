package ai.indra.core.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Epoch-millis time helpers using only java.text/java.util (Android safe). */
public final class Times {

    public static final long SECOND = 1000L;
    public static final long MINUTE = 60L * SECOND;
    public static final long HOUR = 60L * MINUTE;
    public static final long DAY = 24L * HOUR;

    private static final ThreadLocal<SimpleDateFormat> ISO_UTC = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format;
        }
    };

    private static final ThreadLocal<SimpleDateFormat> DATE_ONLY_UTC = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format;
        }
    };

    private Times() {
    }

    public static String toIso(long millis) {
        return ISO_UTC.get().format(new Date(millis));
    }

    public static String toDateOnly(long millis) {
        return DATE_ONLY_UTC.get().format(new Date(millis));
    }

    public static long parseIso(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        String text = value.trim();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            // fall through to ISO parsing
        }
        try {
            return ISO_UTC.get().parse(text).getTime();
        } catch (ParseException error) {
            return 0L;
        }
    }

    /** Formats a timestamp in the user's time zone, e.g. "Sun 16 Nov, 09:00". */
    public static String toDisplay(long millis, String timeZoneId) {
        TimeZone zone = TimeZone.getTimeZone(timeZoneId == null ? "UTC" : timeZoneId);
        SimpleDateFormat format = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH);
        format.setTimeZone(zone);
        return format.format(new Date(millis));
    }

    /** Formats just the clock time in the given zone. */
    public static String toClock(long millis, String timeZoneId) {
        TimeZone zone = TimeZone.getTimeZone(timeZoneId == null ? "UTC" : timeZoneId);
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        format.setTimeZone(zone);
        return format.format(new Date(millis));
    }

    /** True when the two timestamps fall on the same calendar day in the zone. */
    public static boolean sameDay(long a, long b, String timeZoneId) {
        TimeZone zone = TimeZone.getTimeZone(timeZoneId == null ? "UTC" : timeZoneId);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        format.setTimeZone(zone);
        return format.format(new Date(a)).equals(format.format(new Date(b)));
    }

    /** Offset of the given zone from UTC at this instant, in milliseconds. */
    public static long zoneOffsetMillis(long millis, String timeZoneId) {
        TimeZone zone = TimeZone.getTimeZone(timeZoneId == null ? "UTC" : timeZoneId);
        return zone.getOffset(millis);
    }
}
