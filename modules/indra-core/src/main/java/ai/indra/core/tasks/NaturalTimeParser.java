package ai.indra.core.tasks;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understands spoken/typed reminders in English and Nepali, e.g.
 * "remind me tomorrow at 9 am", "in 2 hours", "भोलि बिहान ९ बजे".
 *
 * <p>Everything is computed against a caller-supplied "now" and time zone so the
 * behaviour is deterministic and unit-testable.</p>
 */
public final class NaturalTimeParser {

    /** Parse outcome. */
    public static final class Result {
        private final boolean found;
        private final long millis;
        private final String matchedText;
        private final boolean explicitTime;

        Result(boolean found, long millis, String matchedText, boolean explicitTime) {
            this.found = found;
            this.millis = millis;
            this.matchedText = matchedText;
            this.explicitTime = explicitTime;
        }

        public static Result notFound() {
            return new Result(false, 0L, "", false);
        }

        public boolean isFound() {
            return found;
        }

        public long getMillis() {
            return millis;
        }

        public String getMatchedText() {
            return matchedText;
        }

        /** True when the user named an exact clock time. */
        public boolean hasExplicitTime() {
            return explicitTime;
        }

        public String display(String timeZoneId) {
            return ai.indra.core.util.Times.toDisplay(millis, timeZoneId);
        }
    }

    private static final Pattern IN_RELATIVE = Pattern.compile(
            "\\bin\\s+(\\d{1,3})\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)\\b");

    private static final Pattern RELATIVE_LATER = Pattern.compile(
            "(\\d{1,3})\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?)\\s*(from now|later)");

    private static final Pattern NEPALI_RELATIVE = Pattern.compile(
            "(\\d{1,3})\\s*(सेकेन्ड|मिनेट|घण्टा|घन्टा|दिन|हप्ता|महिना|वर्ष)\\s*(पछि)");

    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern SLASH_DATE = Pattern.compile("\\b(\\d{1,2})[/](\\d{1,2})(?:[/](\\d{2,4}))?\\b");
    private static final Pattern DMY_DATE = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(january|february|march|april|may|june|july|august|september|october|november|december|"
                    + "jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)(?:\\s+(\\d{4}))?\\b");
    private static final Pattern MDY_DATE = Pattern.compile(
            "\\b(january|february|march|april|may|june|july|august|september|october|november|december|"
                    + "jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b");

    private static final Pattern TIME_24 = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern TIME_AMPM = Pattern.compile("\\b(\\d{1,2})(?::([0-5]\\d))?\\s*(am|pm)\\b");
    private static final Pattern TIME_WITH_AT = Pattern.compile("\\bat\\s+(\\d{1,2})(?::([0-5]\\d))?\\b");
    private static final Pattern NEPALI_TIME = Pattern.compile("(\\d{1,2})(?::([0-5]\\d))?\\s*बजे");
    private static final Pattern NEPALI_TIME_WORD = Pattern.compile("बजे");

    private static final Pattern WEEKDAY = Pattern.compile(
            "\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\\b");

    private static final String[] MONTHS = {"january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"};

    private NaturalTimeParser() {
    }

    /** Parses a natural-language time expression. */
    public static Result parse(String text, long nowMillis, String timeZoneId) {
        if (text == null || text.trim().isEmpty()) {
            return Result.notFound();
        }
        String normalized = normalize(text);
        TimeZone zone = TimeZone.getTimeZone(timeZoneId == null ? "UTC" : timeZoneId);

        // 1. Explicit relative offsets win over everything else.
        Matcher relative = IN_RELATIVE.matcher(normalized);
        if (relative.find()) {
            long millis = nowMillis + unitMillis(relative.group(2), Integer.parseInt(relative.group(1)));
            return new Result(true, millis, relative.group(), true);
        }
        Matcher later = RELATIVE_LATER.matcher(normalized);
        if (later.find()) {
            long millis = nowMillis + unitMillis(later.group(2), Integer.parseInt(later.group(1)));
            return new Result(true, millis, later.group(), true);
        }
        Matcher nepaliRelative = NEPALI_RELATIVE.matcher(normalized);
        if (nepaliRelative.find()) {
            long millis = nowMillis + unitMillis(nepaliRelative.group(2), Integer.parseInt(nepaliRelative.group(1)));
            return new Result(true, millis, nepaliRelative.group(), true);
        }

        // 2. "now"
        if (normalized.contains("right now") || normalized.contains("ahile") || normalized.contains("अहिले")
                || normalized.trim().equals("now")) {
            return new Result(true, roundToMinute(nowMillis, zone), "now", true);
        }

        Integer dayOffset = dayOffset(normalized);
        Integer weekdayTarget = weekdayTarget(normalized);

        // 3. Absolute dates
        Calendar explicitDate = explicitDate(normalized, zone, nowMillis);

        // 4. Clock time
        int[] clock = clockTime(normalized);
        boolean explicitTime = clock != null;

        if (explicitDate == null && dayOffset == null && weekdayTarget == null && clock == null) {
            return Result.notFound();
        }

        Calendar calendar = Calendar.getInstance(zone);
        if (explicitDate != null) {
            calendar = explicitDate;
        } else {
            calendar.setTimeInMillis(nowMillis);
            if (weekdayTarget != null) {
                int current = toCalendarWeekday(weekdayTarget.intValue());
                int guard = 0;
                do {
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    guard++;
                } while (calendar.get(Calendar.DAY_OF_WEEK) != current && guard < 8);
            } else if (dayOffset != null) {
                calendar.add(Calendar.DAY_OF_MONTH, dayOffset.intValue());
            }
        }

        int hour;
        int minute;
        if (clock != null) {
            hour = clock[0];
            minute = clock[1];
        } else {
            hour = defaultHour(normalized);
            minute = 0;
        }
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        boolean timeWasGuessed = clock == null && !normalized.contains("बजे");
        if (timeWasGuessed && explicitDate == null && dayOffset == null && weekdayTarget == null) {
            calendar.setTimeInMillis(nowMillis);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
        }
        // A bare clock time in the past means "tomorrow".
        if (explicitDate == null && dayOffset == null && weekdayTarget == null && clock != null
                && calendar.getTimeInMillis() <= nowMillis) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        if (explicitDate == null && (dayOffset != null || weekdayTarget != null) && clock == null) {
            // No time given: morning default is friendlier than midnight.
            calendar.set(Calendar.HOUR_OF_DAY, hour);
        }
        String matched = explicitDate != null ? "date" : String.valueOf(dayOffset);
        return new Result(true, calendar.getTimeInMillis(), matched, explicitTime || normalized.contains("बजे"));
    }

    /** Converts Devanagari digits and normalises spacing/case. */
    public static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0966' && c <= '\u096F') {
                out.append((char) ('0' + (c - '\u0966')));
            } else {
                out.append(c);
            }
        }
        return out.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static long unitMillis(String unit, int amount) {
        String u = unit.toLowerCase(Locale.ROOT);
        if (u.startsWith("sec")) {
            return amount * 1000L;
        }
        if (u.startsWith("min") || u.startsWith("मिनेट")) {
            return amount * 60L * 1000L;
        }
        if (u.startsWith("hour") || u.startsWith("hr") || u.startsWith("घण्टा") || u.startsWith("घन्टा")) {
            return amount * 60L * 60L * 1000L;
        }
        if (u.startsWith("day") || u.startsWith("दिन")) {
            return amount * 24L * 60L * 60L * 1000L;
        }
        if (u.startsWith("week") || u.startsWith("हप्ता")) {
            return amount * 7L * 24L * 60L * 60L * 1000L;
        }
        if (u.startsWith("month") || u.startsWith("महिना")) {
            return amount * 30L * 24L * 60L * 60L * 1000L;
        }
        if (u.startsWith("year") || u.startsWith("वर्ष")) {
            return amount * 365L * 24L * 60L * 60L * 1000L;
        }
        return amount * 60L * 1000L;
    }

    private static long roundToMinute(long nowMillis, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(nowMillis + 30000L);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static Integer dayOffset(String text) {
        if (text.contains("day after tomorrow") || text.contains("पर्सि") || text.contains("parsi")) {
            return Integer.valueOf(2);
        }
        if (text.contains("tomorrow") || text.contains("भोलि") || text.contains("भोली") || text.contains("bholi")) {
            return Integer.valueOf(1);
        }
        if (text.contains("today") || text.contains("आज") || text.contains("aaja")) {
            return Integer.valueOf(0);
        }
        if (text.contains("next week") || text.contains("अर्को हप्ता")) {
            return Integer.valueOf(7);
        }
        return null;
    }

    private static Integer weekdayTarget(String text) {
        Matcher matcher = WEEKDAY.matcher(text);
        if (matcher.find()) {
            String word = matcher.group(1);
            if (word.startsWith("mon")) {
                return Integer.valueOf(Calendar.MONDAY);
            }
            if (word.startsWith("tue")) {
                return Integer.valueOf(Calendar.TUESDAY);
            }
            if (word.startsWith("wed")) {
                return Integer.valueOf(Calendar.WEDNESDAY);
            }
            if (word.startsWith("thu")) {
                return Integer.valueOf(Calendar.THURSDAY);
            }
            if (word.startsWith("fri")) {
                return Integer.valueOf(Calendar.FRIDAY);
            }
            if (word.startsWith("sat")) {
                return Integer.valueOf(Calendar.SATURDAY);
            }
            if (word.startsWith("sun")) {
                return Integer.valueOf(Calendar.SUNDAY);
            }
        }
        return null;
    }

    private static int toCalendarWeekday(int legacyValue) {
        switch (legacyValue) {
            case Calendar.MONDAY:
                return Calendar.MONDAY;
            case Calendar.TUESDAY:
                return Calendar.TUESDAY;
            case Calendar.WEDNESDAY:
                return Calendar.WEDNESDAY;
            case Calendar.THURSDAY:
                return Calendar.THURSDAY;
            case Calendar.FRIDAY:
                return Calendar.FRIDAY;
            case Calendar.SATURDAY:
                return Calendar.SATURDAY;
            case Calendar.SUNDAY:
            default:
                return Calendar.SUNDAY;
        }
    }

    private static Calendar explicitDate(String text, TimeZone zone, long nowMillis) {
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            return buildDate(zone, Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                    Integer.parseInt(iso.group(3)));
        }
        Matcher dmy = DMY_DATE.matcher(text);
        if (dmy.find()) {
            int day = Integer.parseInt(dmy.group(1));
            int month = monthIndex(dmy.group(2));
            int year = dmy.group(3) == null ? currentYear(zone, nowMillis) : Integer.parseInt(dmy.group(3));
            return buildDate(zone, year, month, day);
        }
        Matcher mdy = MDY_DATE.matcher(text);
        if (mdy.find()) {
            int month = monthIndex(mdy.group(1));
            int day = Integer.parseInt(mdy.group(2));
            int year = mdy.group(3) == null ? currentYear(zone, nowMillis) : Integer.parseInt(mdy.group(3));
            return buildDate(zone, year, month, day);
        }
        Matcher slash = SLASH_DATE.matcher(text);
        if (slash.find()) {
            int first = Integer.parseInt(slash.group(1));
            int second = Integer.parseInt(slash.group(2));
            int year = slash.group(3) == null ? currentYear(zone, nowMillis) : Integer.parseInt(slash.group(3));
            if (year < 100) {
                year += 2000;
            }
            // Day-first is the norm in Nepal; fall back to month-first when impossible.
            if (first > 12 && second <= 12) {
                return buildDate(zone, year, second, first);
            }
            if (second > 12 && first <= 12) {
                return buildDate(zone, year, first, second);
            }
            return buildDate(zone, year, second, first);
        }
        return null;
    }

    private static Calendar buildDate(TimeZone zone, int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, Math.max(0, Math.min(11, month - 1)));
        calendar.set(Calendar.DAY_OF_MONTH, Math.max(1, Math.min(31, day)));
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private static int currentYear(TimeZone zone, long nowMillis) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(nowMillis);
        return calendar.get(Calendar.YEAR);
    }

    private static int monthIndex(String word) {
        String value = word.toLowerCase(Locale.ROOT);
        for (int i = 0; i < MONTHS.length; i++) {
            if (MONTHS[i].equals(value) || MONTHS[i].startsWith(value)) {
                return i + 1;
            }
        }
        return 1;
    }

    private static int[] clockTime(String text) {
        Matcher ampm = TIME_AMPM.matcher(text);
        if (ampm.find()) {
            int hour = Integer.parseInt(ampm.group(1));
            int minute = ampm.group(2) == null ? 0 : Integer.parseInt(ampm.group(2));
            boolean pm = "pm".equals(ampm.group(3));
            hour = hour % 12;
            if (pm) {
                hour += 12;
            }
            return new int[]{hour, minute};
        }
        Matcher nepali = NEPALI_TIME.matcher(text);
        if (nepali.find()) {
            int hour = Integer.parseInt(nepali.group(1));
            int minute = nepali.group(2) == null ? 0 : Integer.parseInt(nepali.group(2));
            // Nepali speakers often say "9 बजे" meaning 9 o'clock; keep 24h if > 12.
            if (hour < 8 && text.contains("बेलुका")) {
                hour += 12;
            }
            return new int[]{Math.min(23, hour), minute};
        }
        Matcher at = TIME_WITH_AT.matcher(text);
        if (at.find()) {
            int hour = Integer.parseInt(at.group(1));
            int minute = at.group(2) == null ? 0 : Integer.parseInt(at.group(2));
            if (hour <= 12 && (text.contains("evening") || text.contains("night") || text.contains("बेलुका")
                    || text.contains("राति")) && hour < 12) {
                hour += 12;
            }
            return new int[]{Math.min(23, hour), minute};
        }
        Matcher twentyFour = TIME_24.matcher(text);
        if (twentyFour.find()) {
            return new int[]{Integer.parseInt(twentyFour.group(1)), Integer.parseInt(twentyFour.group(2))};
        }
        return null;
    }

    private static int defaultHour(String text) {
        if (text.contains("morning") || text.contains("बिहान")) {
            return 8;
        }
        if (text.contains("afternoon") || text.contains("दिउँसो")) {
            return 13;
        }
        if (text.contains("evening") || text.contains("बेलुका")) {
            return 18;
        }
        if (text.contains("night") || text.contains("राति")) {
            return 21;
        }
        if (text.contains("midnight") || text.contains("मध्यरात")) {
            return 0;
        }
        if (text.contains("noon") || text.contains("दोपहर")) {
            return 12;
        }
        return 9;
    }

    /** Strips the time expression from a message to obtain the reminder title. */
    public static String extractTitle(String message) {
        if (message == null) {
            return "";
        }
        String title = message;
        title = title.replaceAll("(?i)^\\s*(hey\\s+)?(indra|इन्द्र)[,]?\\s*", "");
        title = title.replaceAll("(?i)\\b(please\\s+)?remind me (to|that|about)?\\s*", "");
        title = title.replaceAll("(?i)\\b(set (a )?(reminder|alarm) (for|to)|create (a )?(reminder|task) (for|to)|add (a )?task)\\b", "");
        title = title.replaceAll("(?i)\\b(in|after)\\s+\\d{1,3}\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)\\b", "");
        title = title.replaceAll("\\d{1,3}\\s*(सेकेन्ड|मिनेट|घण्टा|घन्टा|दिन|हप्ता|महिना|वर्ष)\\s*पछि", "");
        title = title.replaceAll("(?i)\\b(today|tomorrow|day after tomorrow|tonight|this evening|this morning)\\b", "");
        title = title.replaceAll("(?i)\\b(at|by|before|on)\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?\\b", "");
        title = title.replaceAll("(?i)\\b\\d{1,2}(:\\d{2})?\\s*(am|pm)\\b", "");
        title = title.replaceAll("(?i)\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b", "");
        title = title.replaceAll("(?i)\\b(on\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", "");
        title = title.replaceAll("(?i)^\\s*(to|that|about)\\s+", "");
        title = title.replaceAll("\\s{2,}", " ").trim();
        title = title.replaceAll("^[,.:;-]+|[,.:;-]+$", "").trim();
        return title.isEmpty() ? message.trim() : title;
    }
}
