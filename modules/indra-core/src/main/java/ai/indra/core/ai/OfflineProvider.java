package ai.indra.core.ai;

import ai.indra.core.intent.Intent;
import ai.indra.core.intent.IntentClassifier;
import ai.indra.core.intent.IntentResult;
import ai.indra.core.model.Language;
import ai.indra.core.util.Times;
import ai.indra.core.voice.TextLanguageDetector;

/**
 * Deterministic local brain used when no provider is configured or the network
 * is unavailable. It never invents an answer: it answers what it genuinely can
 * (time/date/greeting) and otherwise tells the user exactly how to enable the
 * full AI brain.
 */
public final class OfflineProvider implements AiProvider {

    @Override
    public String getId() {
        return "offline";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public ChatResult complete(ChatRequest request) {
        long started = System.currentTimeMillis();
        String userText = lastUserMessage(request);
        Language language = TextLanguageDetector.detect(userText);
        IntentResult intent = IntentClassifier.classify(userText, started, java.util.TimeZone.getDefault().getID());
        String zone = java.util.TimeZone.getDefault().getID();
        String answer;
        if (intent.getIntent() == Intent.TIME_NOW) {
            answer = language == Language.NEPALI
                    ? "अहिले " + Times.toClock(started, zone) + " बजेको छ, " + Times.toDateOnly(started) + "।"
                    : "It's " + Times.toClock(started, zone) + " on " + Times.toDateOnly(started) + ".";
        } else if (userText.trim().matches("(?i)^(hi|hello|hey|namaste|नमस्ते|hey indra|indra)[\\s!.,]*$")) {
            answer = language == Language.NEPALI
                    ? "नमस्ते! म इन्द्र हुँ। अहिले म अफलाइन मोडमा छु - सेटिङमा गएर एउटा AI कुञ्जी थप्नुहोस्, अनि म पूरा कुरा गर्न सक्छु।"
                    : "Hey, I'm Indra. I'm in offline mode right now - add an AI provider key in Settings and I'll be fully awake.";
        } else {
            answer = language == Language.NEPALI
                    ? "अहिले म अफलाइन मोडमा छु र यो प्रश्नको भरपर्दो उत्तर दिन सक्दिनँ। सेटिङ → AI Providers मा कुञ्जी थप्नुहोस् (OpenAI, DeepSeek, Gemini वा GitHub Models)।"
                    : "I'm in offline mode, so I can't answer that reliably without inventing something. "
                    + "Add a key under Settings → AI Providers (OpenAI, DeepSeek, Gemini, Anthropic, OpenRouter or GitHub Models) "
                    + "and I'll answer properly. Time and date questions still work offline.";
        }
        return new ChatResult(answer, "offline", "local-rules", System.currentTimeMillis() - started, 0, 0, true);
    }

    @Override
    public void stream(ChatRequest request, TokenListener listener) throws AiProviderException {
        ChatResult result = complete(request);
        listener.onToken(result.getText());
        listener.onComplete(result.getText());
    }

    private String lastUserMessage(ChatRequest request) {
        String text = "";
        if (request != null) {
            for (AiMessage message : request.getMessages()) {
                if ("user".equals(message.getRole())) {
                    text = message.getContent();
                }
            }
        }
        return text;
    }
}
