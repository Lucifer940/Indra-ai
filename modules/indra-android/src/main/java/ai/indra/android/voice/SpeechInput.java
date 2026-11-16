package ai.indra.android.voice;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import ai.indra.core.model.Language;
import ai.indra.core.util.Log;

import java.util.ArrayList;

/**
 * Speech recognition wrapper. The microphone is opened only for an explicit
 * listening session started by the user (button press or the visible hands-free
 * service) - nothing is recorded in the background or written to disk.
 */
public final class SpeechInput {

    /** Recognition callbacks delivered on the main thread. */
    public interface Listener {
        void onReadyForSpeech();

        void onPartialResult(String text);

        void onFinalResult(String text, float confidence);

        void onError(String message);

        void onEndOfSpeech();
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Listener listener;
    private Language language = Language.ENGLISH;
    private boolean listening;
    private int retryCount;

    public SpeechInput(Context context) {
        this.context = context.getApplicationContext();
    }

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public boolean isListening() {
        return listening;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setLanguage(Language language) {
        this.language = language == null ? Language.ENGLISH : language;
    }

    public void start() {
        if (!hasPermission(context)) {
            if (listener != null) {
                listener.onError("Microphone permission is needed to listen");
            }
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (listener != null) {
                listener.onError("Speech recognition is not available on this device");
            }
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (recognizer == null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                    recognizer.setRecognitionListener(new RecognitionListener() {
                        @Override
                        public void onReadyForSpeech(Bundle params) {
                            listening = true;
                            retryCount = 0;
                            if (listener != null) {
                                listener.onReadyForSpeech();
                            }
                        }

                        @Override
                        public void onBeginningOfSpeech() {
                            // no-op
                        }

                        @Override
                        public void onRmsChanged(float rmsdB) {
                            if (listener != null) {
                                listener.onPartialResult("");
                            }
                        }

                        @Override
                        public void onBufferReceived(byte[] buffer) {
                            // Audio buffers are intentionally ignored: nothing is stored.
                        }

                        @Override
                        public void onEndOfSpeech() {
                            listening = false;
                            if (listener != null) {
                                listener.onEndOfSpeech();
                            }
                        }

                        @Override
                        public void onError(int error) {
                            listening = false;
                            if (listener != null) {
                                listener.onError(describeError(error));
                            }
                        }

                        @Override
                        public void onResults(Bundle results) {
                            listening = false;
                            ArrayList<String> matches = results == null ? null
                                    : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            float[] scores = results == null ? null
                                    : results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                            if (matches == null || matches.isEmpty()) {
                                if (listener != null) {
                                    listener.onError("I didn't catch that - try again a bit closer to the mic");
                                }
                                return;
                            }
                            float confidence = scores != null && scores.length > 0 ? scores[0] : 0.6f;
                            if (listener != null) {
                                listener.onFinalResult(matches.get(0), confidence);
                            }
                        }

                        @Override
                        public void onPartialResults(Bundle partialResults) {
                            ArrayList<String> matches = partialResults == null ? null
                                    : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty() && listener != null) {
                                listener.onPartialResult(matches.get(0));
                            }
                        }

                        @Override
                        public void onEvent(int eventType, Bundle params) {
                            // reserved
                        }
                    });
                }
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language == Language.NEPALI ? "ne-NP" : "en-US");
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language == Language.NEPALI ? "ne-NP" : "en-US");
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
                try {
                    recognizer.startListening(intent);
                    listening = true;
                } catch (SecurityException error) {
                    listening = false;
                    if (listener != null) {
                        listener.onError("Microphone permission was denied");
                    }
                }
            }
        });
    }

    /** Restarts recognition after a transient failure (hands-free mode). */
    public void restartSoon(int delayMillis) {
        if (retryCount++ > 6) {
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                start();
            }
        }, Math.max(400, delayMillis));
    }

    public void stop() {
        listening = false;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (recognizer != null) {
                    try {
                        recognizer.stopListening();
                    } catch (RuntimeException error) {
                        Log.warn("SpeechInput", "stopListening failed: " + error.getMessage());
                    }
                }
            }
        });
    }

    public void destroy() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (recognizer != null) {
                    recognizer.destroy();
                    recognizer = null;
                }
            }
        });
        listening = false;
    }

    private static String describeError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Microphone error - check that no other app is using it";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Recognition was cancelled";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone permission is required";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Speech recognition needs a network connection right now";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "I didn't catch that - please say it again";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "The recogniser is busy - try again in a moment";
            case SpeechRecognizer.ERROR_SERVER:
                return "The speech service had a problem - try again";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "I didn't hear anything";
            default:
                return "Speech recognition failed - please try again";
        }
    }
}
