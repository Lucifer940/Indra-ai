package ai.indra.core.voice;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns model output into something that sounds natural when spoken: markdown,
 * emoji, tables and code fences are removed and long answers are split into
 * speakable chunks.
 */
public final class SpeechSanitizer {

    private SpeechSanitizer() {
    }

    public static String forSpeech(String text) {
        if (text == null) {
            return "";
        }
        String out = text;
        // Fenced code blocks -> short spoken note.
        out = out.replaceAll("(?s)```[a-zA-Z0-9+\\-]*\\n?", " Code block: ");
        out = out.replaceAll("```", " ");
        // Inline code, bold, italics, strikethrough.
        out = out.replaceAll("`([^`]*)`", "$1");
        out = out.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        out = out.replaceAll("__([^_]+)__", "$1");
        out = out.replaceAll("(?<!\\w)[*_]([^*_]+)[*_](?!\\w)", "$1");
        // Links: keep the visible text.
        out = out.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1");
        // Headings and bullets.
        out = out.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        out = out.replaceAll("(?m)^\\s*[-*+]\\s+", ". ");
        out = out.replaceAll("(?m)^\\s*\\d+\\.\\s+", ". ");
        // Tables / horizontal rules.
        out = out.replaceAll("(?m)^\\s*\\|?\\s*:?-{3,}.*$", "");
        out = out.replaceAll("\\|", ", ");
        // Emoji and assorted symbols.
        out = out.replaceAll("[\\u2190-\\u21FF\\u2300-\\u27BF\\u2B00-\\u2BFF\\uFE0F\\u200D]", " ");
        out = out.replaceAll("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]", " ");
        // Symbols that read badly.
        out = out.replace("%", " percent ");
        out = out.replace("&", " and ");
        out = out.replace("=", " equals ");
        out = out.replace("→", " to ");
        out = out.replace("->", " to ");
        out = out.replace("#", " number ");
        out = out.replaceAll("\\s+", " ");
        out = out.replace(" .", ".").replace(" ,", ",").replaceAll("\\s{2,}", " ").trim();
        return out;
    }

    /** Splits text into TTS-friendly chunks of at most {@code maxChars}. */
    public static List<String> chunk(String text, int maxChars) {
        List<String> chunks = new ArrayList<String>();
        String clean = forSpeech(text);
        if (clean.isEmpty()) {
            return chunks;
        }
        int limit = Math.max(80, maxChars);
        if (clean.length() <= limit) {
            chunks.add(clean);
            return chunks;
        }
        String[] sentences = clean.split("(?<=[.!?।])\\s+");
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.length() > 0 && current.length() + sentence.length() > limit) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (sentence.length() > limit) {
                int start = 0;
                while (start < sentence.length()) {
                    int end = Math.min(sentence.length(), start + limit);
                    chunks.add(sentence.substring(start, end).trim());
                    start = end;
                }
            } else {
                current.append(sentence).append(' ');
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }
}
