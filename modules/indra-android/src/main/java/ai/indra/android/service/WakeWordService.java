package ai.indra.android.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import ai.indra.android.IndraApp;
import ai.indra.android.MainActivity;
import ai.indra.core.model.Language;
import ai.indra.core.util.Log;
import ai.indra.core.voice.WakeWord;
import ai.indra.android.voice.SpeechInput;

/**
 * Visible, opt-in hands-free listening.
 *
 * <p>Design constraints honoured here:</p>
 * <ul>
 *   <li>Starts only when the user turns hands-free on in the app.</li>
 *   <li>Always shows an ongoing notification while the microphone is active.</li>
 *   <li>Never keeps or transmits raw audio - only the on-device recogniser's text.</li>
 *   <li>Stops immediately when the user turns the switch off or says "turn off".</li>
 * </ul>
 */
public class WakeWordService extends Service implements SpeechInput.Listener {

    public static final String ACTION_START = "ai.indra.android.WAKE_START";
    public static final String ACTION_STOP = "ai.indra.android.WAKE_STOP";
    private static final int NOTIFICATION_ID = 4210;

    private SpeechInput speech;
    private boolean active;
    private long lastWakeAt;

    @Override
    public void onCreate() {
        super.onCreate();
        speech = new SpeechInput(this);
        speech.setListener(this);
        Language language = Language.fromCode(IndraApp.from(this).prefs().getLanguage());
        speech.setLanguage(language);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopListening();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ai.indra.android.voice.SpeechInput.hasPermission(this)) {
            Log.warn("WakeWordService", "Cannot listen without RECORD_AUDIO permission");
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, NotificationHelper.voiceServiceNotification(this, true));
        active = true;
        speech.start();
        return START_STICKY;
    }

    /** Entry point remembered by MainActivity so the switch reflects reality. */
    public boolean isActive() {
        return active;
    }

    private void stopListening() {
        active = false;
        if (speech != null) {
            speech.stop();
        }
        IndraApp.from(this).prefs().setWakeWordEnabled(false);
        stopForeground(true);
    }

    @Override
    public void onReadyForSpeech() {
        // keep the notification in the "listening" state
        android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, NotificationHelper.voiceServiceNotification(this, true));
        }
    }

    @Override
    public void onPartialResult(String text) {
        handleTranscript(text);
    }

    @Override
    public void onFinalResult(String text, float confidence) {
        boolean handled = handleTranscript(text);
        if (!handled) {
            speech.restartSoon(500);
        }
    }

    @Override
    public void onError(String message) {
        Log.debug("WakeWordService", "Recognition hiccup: " + message);
        if (active) {
            speech.restartSoon(1200);
        }
    }

    @Override
    public void onEndOfSpeech() {
        if (active && !speech.isListening()) {
            speech.restartSoon(400);
        }
    }

    /** Returns true when the transcript contained the wake word. */
    private boolean handleTranscript(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String wakeWord = IndraApp.from(this).prefs().getWakeWord();
        if (!WakeWord.contains(text, wakeWord)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastWakeAt < 1500L) {
            return true;
        }
        lastWakeAt = now;
        String command = WakeWord.extractCommand(text, wakeWord);
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("voiceCommand", command == null ? "" : command);
        open.putExtra("fromWakeWord", true);
        startActivity(open);
        NotificationHelper.showSimple(this, "Indra", command == null || command.isEmpty()
                ? "I'm listening" : "Opening: " + command);
        return true;
    }

    @Override
    public void onDestroy() {
        active = false;
        if (speech != null) {
            speech.destroy();
            speech = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
