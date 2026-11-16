package ai.indra.android.voice;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import ai.indra.core.model.Language;
import ai.indra.core.util.Log;
import ai.indra.core.voice.SpeechSanitizer;
import ai.indra.core.voice.TextLanguageDetector;

import java.util.List;
import java.util.Locale;

/**
 * Text-to-speech with sentence chunking and mouth-sync callbacks.
 *
 * <p>"Indra" is a female assistant, so the Indonesian/Indian English voices (or
 * the device default female voice) are preferred by name when present.</p>
 */
public final class TtsSpeaker {

    /** UI hooks for the animated character. */
    public interface Listener {
        void onStartSpeaking();

        void onMouthLevel(float level);

        void onDoneSpeaking();

        void onError(String message);
    }

    private final Context context;
    private TextToSpeech tts;
    private boolean ready;
    private Listener listener;
    private Runnable pendingSpeech;

    public TtsSpeaker(Context context) {
        this.context = context.getApplicationContext();
        init();
    }

    private void init() {
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ready = status == TextToSpeech.SUCCESS;
                if (!ready) {
                    Log.warn("TtsSpeaker", "Text-to-speech is unavailable on this device");
                    return;
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        if (listener != null) {
                            listener.onStartSpeaking();
                        }
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        if ("final".equals(utteranceId)) {
                            if (listener != null) {
                                listener.onMouthLevel(0f);
                                listener.onDoneSpeaking();
                            }
                        } else if (listener != null) {
                            listener.onMouthLevel(0.25f);
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        if (listener != null) {
                            listener.onError("Speech synthesis failed");
                        }
                    }
                });
                if (pendingSpeech != null) {
                    Runnable runnable = pendingSpeech;
                    pendingSpeech = null;
                    runnable.run();
                }
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isReady() {
        return ready;
    }

    /** Applies language, rate and pitch from settings. */
    public void apply(Language language, float rate, float pitch) {
        if (!ready) {
            return;
        }
        Locale locale = language == Language.NEPALI ? new Locale("ne", "NP") : Locale.US;
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US);
        }
        tts.setSpeechRate(Math.max(0.5f, Math.min(2.0f, rate)));
        tts.setPitch(Math.max(0.5f, Math.min(2.0f, pitch)));
    }

    /** Speaks text, stripping markdown and splitting long answers into chunks. */
    public void speak(String text, final Language language) {
        final String clean = SpeechSanitizer.forSpeech(text);
        if (clean.trim().isEmpty()) {
            return;
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                tts.stop();
                List<String> chunks = SpeechSanitizer.chunk(clean, 380);
                for (int i = 0; i < chunks.size(); i++) {
                    Bundle params = new Bundle();
                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
                    String id = i == chunks.size() - 1 ? "final" : "chunk-" + i;
                    tts.speak(chunks.get(i), TextToSpeech.QUEUE_ADD, params, id);
                }
            }
        };
        if (ready) {
            task.run();
        } else {
            pendingSpeech = task;
        }
    }

    public void stop() {
        if (ready) {
            tts.stop();
        }
        if (listener != null) {
            listener.onMouthLevel(0f);
            listener.onDoneSpeaking();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ready = false;
        }
    }

    public Language detectLanguage(String text, Language fallback) {
        Language detected = TextLanguageDetector.detect(text);
        return detected == null ? fallback : detected;
    }
}
