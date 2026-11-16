package ai.indra.core.intent;

import ai.indra.core.json.JsonObject;
import ai.indra.core.tasks.NaturalTimeParser;
import ai.indra.core.util.Strings;

import java.util.Locale;

/**
 * Fast local intent recognition for commands that should not wait for a network
 * round-trip. Anything not recognised falls through to the language model as
 * {@link Intent#CHAT}, so the assistant never becomes less capable - only faster
 * for clear commands. English and Nepali phrases are both supported.
 */
public final class IntentClassifier {

    private IntentClassifier() {
    }

    public static IntentResult classify(String message, long nowMillis, String timeZone) {
        if (Strings.isBlank(message)) {
            return IntentResult.chat();
        }
        String text = NaturalTimeParser.normalize(message);
        String padded = " " + text.replaceAll("[!?.,।]+", " ") + " ";

        // Order matters: destructive/sensitive intents first.
        if (matches(padded, " stop talking", " stop speaking", " be quiet", " chup", " चुप ", " बन्द गर आवाज")) {
            return result(Intent.VOICE_STOP, 0.95d, new JsonObject(), RiskLevel.SAFE, "stop");
        }
        if (matches(padded, " turn off indra", " go to sleep", " sleep now", " सुत ", " बन्द होऊ", " off ho")) {
            return result(Intent.ASSISTANT_OFF, 0.9d, new JsonObject(), RiskLevel.LOW, "turn off");
        }
        if (matches(padded, " forget that", " forget about", " forget ", " delete memory", " remove memory",
                " बिर्सनु ", " सम्झनाबाट ")) {
            String topic = MemoryTopics.topic(message);
            JsonObject slots = new JsonObject();
            slots.put("topic", topic);
            return result(Intent.MEMORY_FORGET, 0.9d, slots, RiskLevel.ELEVATED, "forget");
        }
        if (matches(padded, " what do you remember", " what do you know about me", " my memories", " show memory",
                " list memory", " सम्झना देखाउ ")) {
            return result(Intent.MEMORY_LIST, 0.9d, new JsonObject(), RiskLevel.SAFE, "memories");
        }
        if (matches(padded, " clear all memories", " delete all memories", " clear my memory", " सबै सम्झना मेट्नु ")) {
            return result(Intent.MEMORY_CLEAR, 0.95d, new JsonObject(), RiskLevel.HIGH, "clear memories");
        }
        if (matches(padded, " remember that", " remember ", " note that", " keep in mind", " सम्झनुस् ", " याद राख्नु ")) {
            return result(Intent.MEMORY_REMEMBER, 0.88d, new JsonObject(), RiskLevel.LOW, "remember");
        }

        NaturalTimeParser.Result time = NaturalTimeParser.parse(message, nowMillis, timeZone);
        if (matches(padded, " remind me", " reminder", " alarm ", " set an alarm", " set alarm", " wake me",
                " सम्झाउनु ", " अलार्म ", " रिमाइन्डर ")) {
            JsonObject slots = new JsonObject();
            slots.put("title", NaturalTimeParser.extractTitle(message));
            slots.put("whenText", time.isFound() ? time.getMatchedText() : "");
            slots.put("at", Long.valueOf(time.isFound() ? time.getMillis() : 0L));
            slots.put("hasTime", Boolean.valueOf(time.isFound()));
            RiskLevel risk = time.isFound() ? RiskLevel.LOW : RiskLevel.ELEVATED;
            return result(Intent.REMINDER_CREATE, 0.92d, slots, risk, "remind");
        }
        if (matches(padded, " add task", " new task", " create task", " add a task", " todo ", " to-do ",
                " काम थप्नु ", " टास्क ")) {
            JsonObject slots = new JsonObject();
            slots.put("title", NaturalTimeParser.extractTitle(message));
            slots.put("at", Long.valueOf(time.isFound() ? time.getMillis() : 0L));
            return result(Intent.TASK_CREATE, 0.9d, slots, RiskLevel.LOW, "task");
        }
        if (matches(padded, " my tasks", " show tasks", " list tasks", " what are my tasks", " today's tasks",
                " कामहरू ", " मेरा काम ")) {
            return result(Intent.TASK_LIST, 0.92d, new JsonObject(), RiskLevel.SAFE, "tasks");
        }
        if (matches(padded, " mark task", " complete task", " task done", " mark done", " finished task", " सकियो भन्नु ")) {
            JsonObject slots = new JsonObject();
            slots.put("title", NaturalTimeParser.extractTitle(message));
            return result(Intent.TASK_COMPLETE, 0.85d, slots, RiskLevel.LOW, "complete");
        }
        if (matches(padded, " delete task", " remove task", " cancel reminder", " cancel alarm", " काम मेट्नु ")) {
            JsonObject slots = new JsonObject();
            slots.put("title", NaturalTimeParser.extractTitle(message));
            return result(Intent.TASK_DELETE, 0.85d, slots, RiskLevel.ELEVATED, "delete task");
        }

        if (matches(padded, " send sms", " send a text", " text message to", " send message to", " म्यासेज पठाउ ")) {
            JsonObject slots = new JsonObject();
            slots.put("recipient", ContactSlots.recipient(message));
            slots.put("body", ContactSlots.body(message));
            return result(Intent.SEND_SMS, 0.85d, slots, RiskLevel.HIGH, "sms");
        }
        if (matches(padded, " whatsapp ", " send whatsapp", " wa.me", " व्हाट्सएप ")) {
            JsonObject slots = new JsonObject();
            slots.put("recipient", ContactSlots.recipient(message));
            slots.put("body", ContactSlots.body(message));
            return result(Intent.WHATSAPP, 0.85d, slots, RiskLevel.HIGH, "whatsapp");
        }
        if (matches(padded, " call ") && !padded.contains(" call me maybe")) {
            JsonObject slots = new JsonObject();
            slots.put("recipient", ContactSlots.recipient(message));
            return result(Intent.CALL, 0.8d, slots, RiskLevel.HIGH, "call");
        }

        if (matches(padded, " open app", " launch ", " open application", " खोल ")) {
            JsonObject slots = new JsonObject();
            slots.put("app", AppSlots.appName(message));
            return result(Intent.OPEN_APP, 0.85d, slots, RiskLevel.LOW, "open app");
        }
        if (matches(padded, " open http", " open www", " open website", " browse ", " वेबसाइट खोल ")) {
            JsonObject slots = new JsonObject();
            slots.put("url", AppSlots.url(message));
            return result(Intent.OPEN_URL, 0.88d, slots, RiskLevel.LOW, "open url");
        }

        if (matches(padded, " weather", " temperature", " forecast", " rain ", " मौसम ", " तापक्रम ")) {
            JsonObject slots = new JsonObject();
            slots.put("location", WeatherSlots.location(message));
            return result(Intent.WEATHER, 0.9d, slots, RiskLevel.SAFE, "weather");
        }
        if (matches(padded, " news", " headlines", " समाचार ", " खबर ")) {
            return result(Intent.NEWS, 0.85d, new JsonObject(), RiskLevel.SAFE, "news");
        }
        if (matches(padded, " search google", " search the web", " google ", " look up ", " find online",
                " खोज्नु ", " गुगल ")) {
            JsonObject slots = new JsonObject();
            slots.put("query", SearchSlots.query(message));
            return result(Intent.WEB_SEARCH, 0.88d, slots, RiskLevel.SAFE, "search");
        }
        if (matches(padded, " what time is it", " current time", " today's date", " what is the date",
                " समय कति ", " आजको मिति ")) {
            return result(Intent.TIME_NOW, 0.95d, new JsonObject(), RiskLevel.SAFE, "time");
        }
        if (matches(padded, " battery", " ram usage", " cpu usage", " system info", " disk space", " ब्याट्री ")) {
            return result(Intent.SYSTEM_INFO, 0.9d, new JsonObject(), RiskLevel.SAFE, "system");
        }
        if (matches(padded, " generate image", " create an image", " draw ", " picture of", " तस्बिर बनाउ ")) {
            JsonObject slots = new JsonObject();
            slots.put("prompt", SearchSlots.query(message));
            return result(Intent.IMAGE_GENERATE, 0.85d, slots, RiskLevel.LOW, "image");
        }
        if (matches(padded, " whiteboard", " open drawing", " sketch pad", " चित्र बोर्ड ")) {
            return result(Intent.WHITEBOARD, 0.9d, new JsonObject(), RiskLevel.SAFE, "whiteboard");
        }
        if (matches(padded, " play music", " play song", " pause music", " stop music", " volume up", " गीत बजाउ ")) {
            return result(Intent.MEDIA_PLAY, 0.85d, new JsonObject(), RiskLevel.LOW, "music");
        }
        if (matches(padded, " translate", " अनुवाद ")) {
            JsonObject slots = new JsonObject();
            slots.put("text", SearchSlots.query(message));
            return result(Intent.TRANSLATE, 0.85d, slots, RiskLevel.SAFE, "translate");
        }
        if (matches(padded, " solve ", " equation", " homework", " explain chapter", " quiz me", " पढाइ ", " प्रश्न ")) {
            return result(Intent.STUDY, 0.75d, new JsonObject(), RiskLevel.SAFE, "study");
        }
        if (matches(padded, " write code", " debug ", " compile error", " gradle ", " java code", " android code", " कोड ")) {
            return result(Intent.CODE_HELP, 0.8d, new JsonObject(), RiskLevel.SAFE, "code");
        }
        if (matches(padded, " be funny", " professional mode", " study mode", " be romantic", " change personality",
                " personality", " matter of tone")) {
            JsonObject slots = new JsonObject();
            slots.put("persona", PersonaSlots.persona(text));
            return result(Intent.PERSONALITY_SET, 0.85d, slots, RiskLevel.SAFE, "persona");
        }
        if (matches(padded, " jarvis brain", " edith brain", " friday brain", " vision brain", " ultron brain",
                " switch brain", " change brain")) {
            JsonObject slots = new JsonObject();
            slots.put("brain", PersonaSlots.brain(text));
            return result(Intent.BRAIN_SET, 0.85d, slots, RiskLevel.SAFE, "brain");
        }
        if (matches(padded, " speak nepali", " speak english", " नेपाली बोल ", " language change", " change language")) {
            JsonObject slots = new JsonObject();
            slots.put("language", PersonaSlots.language(text));
            return result(Intent.LANGUAGE_SET, 0.9d, slots, RiskLevel.SAFE, "language");
        }
        if (matches(padded, " open settings", " settings", " सेटिङ ")) {
            return result(Intent.SETTINGS_OPEN, 0.8d, new JsonObject(), RiskLevel.SAFE, "settings");
        }
        if (matches(padded, " pair device", " pair with", " connect pc", " link device", " डिभाइस जोड ")) {
            return result(Intent.DEVICE_PAIR, 0.85d, new JsonObject(), RiskLevel.LOW, "pair");
        }
        if (matches(padded, " help ", " what can you do", " commands", " सहयोग ")) {
            return result(Intent.HELP, 0.8d, new JsonObject(), RiskLevel.SAFE, "help");
        }
        if (matches(padded, " delete file", " remove file", " run command", " execute ", " rename file", " move file")) {
            JsonObject slots = new JsonObject();
            slots.put("raw", message);
            return result(Intent.FILE_OPERATION, 0.75d, slots, RiskLevel.HIGH, "file");
        }
        return IntentResult.chat();
    }

    private static boolean matches(String paddedText, String... phrases) {
        for (String phrase : phrases) {
            if (paddedText.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static IntentResult result(Intent intent, double confidence, JsonObject slots, RiskLevel risk, String phrase) {
        return new IntentResult(intent, confidence, slots, risk, phrase);
    }

    /** Slot helpers kept together so the classifier stays readable. */
    static final class SearchSlots {
        private static final String[] PREFIXES = {"search google for", "search the web for", "search for",
            "google", "look up", "find online", "खोज्नु", "गुगल गर्नु", "generate image of", "create an image of",
            "draw", "picture of", "translate", "अनुवाद गर्नु"};

        private SearchSlots() {
        }

        static String query(String message) {
            String text = message == null ? "" : message.trim();
            String lower = text.toLowerCase(Locale.ROOT);
            for (String prefix : PREFIXES) {
                int index = lower.indexOf(prefix);
                if (index >= 0) {
                    String remainder = text.substring(index + prefix.length()).trim();
                    remainder = remainder.replaceAll("(?i)^(for|about|the|a|an)\\s+", "").trim();
                    if (!remainder.isEmpty()) {
                        return remainder;
                    }
                }
            }
            return text;
        }
    }

    static final class AppSlots {
        private AppSlots() {
        }

        static String appName(String message) {
            String text = message == null ? "" : message.trim();
            String lower = text.toLowerCase(Locale.ROOT);
            String[] prefixes = {"open app", "open application", "launch", "open", "खोल"};
            for (String prefix : prefixes) {
                int index = lower.indexOf(prefix);
                if (index >= 0) {
                    String remainder = text.substring(index + prefix.length()).trim();
                    remainder = remainder.replaceAll("(?i)^(the|my|app)\\s+", "").trim();
                    if (!remainder.isEmpty()) {
                        return remainder;
                    }
                }
            }
            return text;
        }

        static String url(String message) {
            String text = message == null ? "" : message.trim();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(https?://\\S+|www\\.\\S+|[a-z0-9\\-]+\\.(com|np|org|net|io|dev)(/\\S*)?)",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(text);
            return matcher.find() ? matcher.group(1) : text;
        }
    }

    static final class ContactSlots {
        private ContactSlots() {
        }

        static String recipient(String message) {
            String text = message == null ? "" : message.trim();
            java.util.regex.Matcher number = java.util.regex.Pattern.compile("(\\+?\\d[\\d\\s\\-]{6,15})").matcher(text);
            if (number.find()) {
                return number.group(1).replaceAll("[\\s\\-]", "");
            }
            java.util.regex.Matcher named = java.util.regex.Pattern
                    .compile("(?i)(?:to|for)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)").matcher(text);
            return named.find() ? named.group(1) : "";
        }

        static String body(String message) {
            String text = message == null ? "" : message.trim();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)(?:saying|say|that says|message:|text:)\\s*[\"']?(.+)$").matcher(text);
            return matcher.find() ? matcher.group(1).trim() : "";
        }
    }

    static final class WeatherSlots {
        private WeatherSlots() {
        }

        static String location(String message) {
            String text = message == null ? "" : message.trim();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)(?:in|at|for)\\s+([A-Za-z\u0900-\u097F\\s]{2,30})$").matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            return "";
        }
    }

    static final class PersonaSlots {
        private PersonaSlots() {
        }

        static String persona(String text) {
            if (text.contains("funny") || text.contains("joke")) {
                return "funny";
            }
            if (text.contains("professional") || text.contains("formal")) {
                return "professional";
            }
            if (text.contains("study") || text.contains("teacher")) {
                return "study";
            }
            if (text.contains("motivat")) {
                return "motivational";
            }
            if (text.contains("dramatic") || text.contains("drama")) {
                return "dramatic";
            }
            if (text.contains("romantic") || text.contains("affection") || text.contains("companion")) {
                return "companion";
            }
            return "friendly";
        }

        static String brain(String text) {
            if (text.contains("edith")) {
                return "tactical";
            }
            if (text.contains("friday")) {
                return "social";
            }
            if (text.contains("vision")) {
                return "analytic";
            }
            if (text.contains("ultron")) {
                return "ultron";
            }
            if (text.contains("jarvis")) {
                return "strategic";
            }
            return "core";
        }

        static String language(String text) {
            if (text.contains("nepali") || text.contains("नेपाली")) {
                return "ne";
            }
            return "en";
        }
    }

    static final class MemoryTopics {
        private MemoryTopics() {
        }

        static String topic(String message) {
            String topic = ai.indra.core.memory.MemoryEngine.detectForgetTopic(message);
            return topic == null ? "" : topic;
        }
    }
}
