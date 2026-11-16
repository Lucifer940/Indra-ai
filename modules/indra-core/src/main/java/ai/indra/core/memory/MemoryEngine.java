package ai.indra.core.memory;

import ai.indra.core.model.MemoryCategory;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.model.MemorySource;
import ai.indra.core.util.Strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Long-term memory logic: detecting explicit "remember this" requests,
 * classifying them, de-duplicating, ranking for recall and formatting into the
 * system prompt.
 */
public final class MemoryEngine {

    public static final int MAX_CONTENT_LENGTH = 240;
    public static final int MAX_MEMORIES_PER_USER = 500;

    /** A memory proposed by the conversation but not yet stored. */
    public static final class Candidate {
        private final String content;
        private final MemoryCategory category;
        private final int importance;
        private final boolean explicit;

        Candidate(String content, MemoryCategory category, int importance, boolean explicit) {
            this.content = content;
            this.category = category;
            this.importance = importance;
            this.explicit = explicit;
        }

        public String getContent() {
            return content;
        }

        public MemoryCategory getCategory() {
            return category;
        }

        public int getImportance() {
            return importance;
        }

        /** True when the user explicitly asked INDRA to remember this. */
        public boolean isExplicit() {
            return explicit;
        }

        public MemoryItem toMemory(String userId) {
            return MemoryItem.create(userId, content, category,
                    explicit ? MemorySource.USER_STATED : MemorySource.AUTO_APPROVED, importance);
        }
    }

    private static final List<String> EXPLICIT_PREFIXES = Arrays.asList(
            "please remember that ", "please remember ", "remember that ", "remember ",
            "note that ", "keep in mind that ", "keep in mind ", "don't forget that ", "do not forget that ",
            "save that ", "store that ", "my profile note: ",
            // Nepali
            "सम्झनुस् कि ", "सम्झनुस् ", "याद राख्नु कि ", "याद राख्नु ", "याद राख कि ", "याद राख ",
            "नोट गर्नु कि ", "नोट गर्नु ", "बिर्सनु हुँदैन कि "
    );

    private static final List<String> AUTO_PATTERNS = Arrays.asList(
            "my name is ", "i am ", "i'm ", "i live in ", "i work as ", "i work at ", "my birthday is ",
            "i like ", "i love ", "i hate ", "i prefer ", "i study ", "i'm studying ", "my favourite ",
            "my favorite ", "i have a ", "i don't like ", "i do not like ", "i use ",
            "मेरो नाम ", "म बस्छु ", "मलाई मन पर्छ ", "मलाई मन पर्दैन ", "मेरो जन्मदिन "
    );

    private static final Set<String> STOP_WORDS = new HashSet<String>(Arrays.asList(
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "to", "of", "in", "on",
            "at", "for", "with", "my", "i", "me", "you", "your", "that", "this", "it", "и", "म", "मेरो",
            "हो", "छ", "र", "मा", "को", "लाई", "गर्ने", "गर्छु"
    ));

    private MemoryEngine() {
    }

    /** Detects an explicit memory request such as "remember that I love momo". */
    public static Candidate detectExplicit(String userMessage) {
        if (Strings.isBlank(userMessage)) {
            return null;
        }
        String text = userMessage.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String prefix : EXPLICIT_PREFIXES) {
            int index = lower.indexOf(prefix);
            if (index < 0) {
                continue;
            }
            String content = text.substring(index + prefix.length()).trim();
            content = cleanup(content);
            if (content.length() < 3) {
                return null;
            }
            return new Candidate(Strings.truncate(content, MAX_CONTENT_LENGTH),
                    classify(content), 5, true);
        }
        return null;
    }

    /** Heuristic used when the user enabled automatic memory. */
    public static Candidate detectAutomatic(String userMessage) {
        if (Strings.isBlank(userMessage) || userMessage.length() < 8) {
            return null;
        }
        String lower = userMessage.toLowerCase(Locale.ROOT).trim();
        if (lower.indexOf('?') >= 0) {
            return null; // questions are usually not facts about the user
        }
        for (String pattern : AUTO_PATTERNS) {
            if (lower.startsWith(pattern) || lower.contains(" " + pattern)) {
                String content = cleanup(userMessage.trim());
                if (content.length() < 6) {
                    return null;
                }
                return new Candidate(Strings.truncate(content, MAX_CONTENT_LENGTH),
                        classify(content), 3, false);
            }
        }
        return null;
    }

    /** Extracts the topic of a "forget ..." request. */
    public static String detectForgetTopic(String userMessage) {
        if (Strings.isBlank(userMessage)) {
            return null;
        }
        String lower = userMessage.toLowerCase(Locale.ROOT);
        List<String> prefixes = Arrays.asList(
                "forget that ", "forget about ", "forget ", "delete the memory about ",
                "delete my memory about ", "remove the memory about ", "remove memory ",
                "बिर्सनु ", "सम्झनाबाट हटाउनु ", "मेट्नु ");
        for (String prefix : prefixes) {
            int index = lower.indexOf(prefix);
            if (index >= 0) {
                String topic = cleanup(userMessage.substring(index + prefix.length()));
                return topic.isEmpty() ? null : topic;
            }
        }
        return null;
    }

    private static String cleanup(String value) {
        String text = value.trim();
        while (text.startsWith(",") || text.startsWith(".") || text.startsWith(":") || text.startsWith("-")
                || text.startsWith("।")) {
            text = text.substring(1).trim();
        }
        text = text.replaceAll("\\s+", " ");
        String lower = text.toLowerCase(Locale.ROOT);
        String[] trailers = {" please", " okay", " ok", " thanks", " thank you", "।"};
        for (String trailer : trailers) {
            if (lower.endsWith(trailer)) {
                text = text.substring(0, text.length() - trailer.length()).trim();
                lower = text.toLowerCase(Locale.ROOT);
            }
        }
        return text;
    }

    /** Category inference from keywords in both languages. */
    public static MemoryCategory classify(String content) {
        String text = Strings.lower(content);
        if (containsAny(text, "birthday", "age ", "जन्मदिन", "उमेर", "my name", "मेरो नाम", "live in", "बस्छु")) {
            return MemoryCategory.PROFILE;
        }
        if (containsAny(text, "like", "love", "hate", "prefer", "favourite", "favorite", "मन पर्छ", "मन पर्दैन")) {
            return MemoryCategory.PREFERENCE;
        }
        if (containsAny(text, "mom", "dad", "father", "mother", "sister", "brother", "wife", "husband",
                "family", "friend", "बुबा", "आमा", "दाजु", "भाइ", "दिदी", "बहिनी", "परिवार", "साथी")) {
            return MemoryCategory.RELATIONSHIP;
        }
        if (containsAny(text, "wake up", "sleep", "routine", "every day", "every morning", "बिहान", "दिनचर्या")) {
            return MemoryCategory.ROUTINE;
        }
        if (containsAny(text, "goal", "want to", "dream", "plan to", "target", "लक्ष्य", "सपना")) {
            return MemoryCategory.GOAL;
        }
        if (containsAny(text, "work", "office", "job", "company", "boss", "client", "काम", "अफिस", "जागिर")) {
            return MemoryCategory.WORK;
        }
        if (containsAny(text, "study", "exam", "college", "school", "subject", "पढाइ", "परीक्षा", "विद्यालय")) {
            return MemoryCategory.STUDY;
        }
        if (containsAny(text, "health", "medicine", "allergy", "doctor", "asthma", "diabetes", "औषधि", "स्वास्थ्य")) {
            return MemoryCategory.HEALTH;
        }
        if (containsAny(text, "phone", "number", "contact", "email", "फोन", "नम्बर", "सम्पर्क")) {
            return MemoryCategory.CONTACT;
        }
        return MemoryCategory.OTHER;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Returns true when two memories say essentially the same thing. */
    public static boolean isSimilar(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        Set<String> left = tokens(a);
        Set<String> right = tokens(b);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        int intersection = 0;
        for (String token : left) {
            if (right.contains(token)) {
                intersection++;
            }
        }
        int union = left.size() + right.size() - intersection;
        double jaccard = union == 0 ? 0 : (double) intersection / union;
        return jaccard >= 0.7d;
    }

    /** Finds the stored memory that best matches a candidate (or null). */
    public static MemoryItem findDuplicate(List<MemoryItem> stored, String content) {
        if (stored == null) {
            return null;
        }
        for (MemoryItem item : stored) {
            if (!item.isDeleted() && isSimilar(item.getContent(), content)) {
                return item;
            }
        }
        return null;
    }

    /** Ranks memories by relevance to the query, importance, pinning and recency. */
    public static List<MemoryItem> rank(List<MemoryItem> memories, String query, int limit) {
        List<MemoryItem> result = new ArrayList<MemoryItem>();
        if (memories == null || memories.isEmpty()) {
            return result;
        }
        Set<String> queryTokens = tokens(query);
        long now = System.currentTimeMillis();
        Map<MemoryItem, Double> scores = new LinkedHashMap<MemoryItem, Double>();
        for (MemoryItem item : memories) {
            if (item.isDeleted()) {
                continue;
            }
            double score = item.getImportance() * 0.8d;
            if (item.isPinned()) {
                score += 4.0d;
            }
            Set<String> itemTokens = tokens(item.getContent());
            if (!queryTokens.isEmpty()) {
                int overlap = 0;
                for (String token : queryTokens) {
                    if (itemTokens.contains(token)) {
                        overlap++;
                    }
                }
                score += overlap * 2.5d;
            }
            long ageMillis = Math.max(0L, now - item.getUpdatedAt());
            long days = ageMillis / (24L * 60L * 60L * 1000L);
            score += Math.max(0d, 3.0d - days * 0.05d);
            scores.put(item, Double.valueOf(score));
        }
        List<Map.Entry<MemoryItem, Double>> entries = new ArrayList<Map.Entry<MemoryItem, Double>>(scores.entrySet());
        java.util.Collections.sort(entries, new java.util.Comparator<Map.Entry<MemoryItem, Double>>() {
            @Override
            public int compare(Map.Entry<MemoryItem, Double> left, Map.Entry<MemoryItem, Double> right) {
                return right.getValue().compareTo(left.getValue());
            }
        });
        for (Map.Entry<MemoryItem, Double> entry : entries) {
            if (result.size() >= Math.max(1, limit)) {
                break;
            }
            result.add(entry.getKey());
        }
        return result;
    }

    /** Formats memories for the system prompt. */
    public static String formatForPrompt(List<MemoryItem> memories, int limit) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (MemoryItem item : memories) {
            if (item.isDeleted()) {
                continue;
            }
            if (count++ >= limit) {
                break;
            }
            out.append("- [").append(item.getCategory().name().toLowerCase(Locale.ROOT)).append("] ")
                    .append(item.getContent()).append('\n');
        }
        return out.toString();
    }

    /** Tokenises text for similarity and ranking; works for Latin and Devanagari. */
    public static Set<String> tokens(String text) {
        Set<String> tokens = new HashSet<String>();
        if (Strings.isBlank(text)) {
            return tokens;
        }
        String normalized = Strings.lower(text);
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                word.append(c);
            } else if (word.length() > 0) {
                addToken(tokens, word.toString());
                word.setLength(0);
            }
        }
        if (word.length() > 0) {
            addToken(tokens, word.toString());
        }
        return tokens;
    }

    private static void addToken(Set<String> tokens, String token) {
        if (token.length() < 2 || STOP_WORDS.contains(token)) {
            return;
        }
        tokens.add(token);
    }
}
