package ai.indra.android;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import ai.indra.android.data.ApiClient;
import ai.indra.android.data.LocalStore;
import ai.indra.android.data.Prefs;
import ai.indra.android.data.SessionManager;
import ai.indra.android.platform.AppControl;
import ai.indra.android.platform.FileExport;
import ai.indra.android.service.NotificationHelper;
import ai.indra.android.service.ReminderScheduler;
import ai.indra.android.service.WakeWordService;
import ai.indra.android.ui.CharacterView;
import ai.indra.android.ui.AuraStyle;
import ai.indra.android.ui.ChatScreen;
import ai.indra.android.ui.MemoryScreen;
import ai.indra.android.ui.HomeScreen;
import ai.indra.android.ui.Screen;
import ai.indra.android.ui.SettingsScreen;
import ai.indra.android.ui.TasksScreen;
import ai.indra.android.ui.UiKit;
import ai.indra.android.ui.VoiceScreen;
import ai.indra.android.voice.SpeechInput;
import ai.indra.android.voice.TtsSpeaker;
import ai.indra.core.intent.IntentClassifier;
import ai.indra.core.intent.IntentResult;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.ChatMessage;
import ai.indra.core.model.Language;
import ai.indra.core.model.MessageRole;
import ai.indra.core.model.MemoryCategory;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.model.MemorySource;
import ai.indra.core.model.TaskItem;
import ai.indra.core.tasks.NaturalTimeParser;
import ai.indra.core.tasks.RecurrenceEngine;
import ai.indra.core.util.Log;
import ai.indra.core.util.Strings;
import ai.indra.core.voice.WakeWord;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main experience: chat, voice, memory, tasks and settings in one shell.
 *
 * <p>All network work runs on a small executor; every callback is marshalled back
 * to the main thread. Device actions (calls, SMS, app launch) always pass through
 * an explicit confirmation dialog.</p>
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 1001;
    private static final int REQ_OAUTH = 1002;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Prefs prefs;
    private LocalStore store;
    private ApiClient api;
    private SessionManager session;

    private FrameLayout content;
    private LinearLayout nav;
    private LinearLayout topBar;
    private TextView connectionStatus;
    private TextView floatingMic;
    private HomeScreen homeScreen;
    private ChatScreen chatScreen;
    private VoiceScreen voiceScreen;
    private MemoryScreen memoryScreen;
    private TasksScreen tasksScreen;
    private SettingsScreen settingsScreen;

    private TtsSpeaker speaker;
    private SpeechInput speech;
    private boolean listening;
    private String conversationId;
    private boolean streaming;
    private int currentTab = 0;

    // ------------------------------------------------------------------ setup

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IndraApp app = IndraApp.from(this);
        prefs = app.prefs();
        store = app.store();
        api = app.api();
        session = app.session();
        conversationId = store.getJson("lastConversation") == null ? null
                : store.getJson("lastConversation").getString("id", null);

        if (!prefs.hasSession()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        applySystemBarAppearance();
        buildShell();
        setupVoice();
        requestStartupPermissions();
        refreshFromServer();
        handleVoiceIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("refreshSession", false)) {
            // Signed in or out from the login screen: rebuild with the new session.
            intent.removeExtra("refreshSession");
            homeScreen = null;
            chatScreen = null;
            voiceScreen = null;
            memoryScreen = null;
            tasksScreen = null;
            settingsScreen = null;
            if (!prefs.hasSession()) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }
            buildShell();
            return;
        }
        handleVoiceIntent(intent);
    }

    private void buildShell() {
        FrameLayout root = UiKit.root(this);
        LinearLayout column = UiKit.vertical(this);

        // Compact brand bar used outside Home (Home draws its own premium header).
        topBar = UiKit.horizontal(this);
        topBar.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 10), UiKit.dp(this, 16), UiKit.dp(this, 4));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.indra_logo);
        logo.setLayoutParams(new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));
        topBar.addView(logo);
        LinearLayout brand = UiKit.vertical(this);
        brand.setPadding(UiKit.dp(this, 10), 0, 0, 0);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        brand.addView(UiKit.text(this, "INDRA AI", 17f, UiKit.TEXT, true));
        connectionStatus = UiKit.text(this, "Connecting…", 10f, UiKit.TEXT_DIM, false);
        brand.addView(connectionStatus);
        topBar.addView(brand);
        Button sync = UiKit.smallButton(this, "Sync");
        sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncNow();
            }
        });
        topBar.addView(sync);
        column.addView(topBar);

        content = new FrameLayout(this);
        column.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = UiKit.horizontal(this);
        nav.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 6), UiKit.dp(this, 4), UiKit.dp(this, 10));
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(UiKit.SURFACE);
        nav.setElevation(UiKit.dp(this, 8));
        column.addView(nav);

        root.addView(column);

        // Floating microphone: always one tap away, above the navigation bar.
        floatingMic = UiKit.text(this, "🎤", 24f, android.graphics.Color.WHITE, false);
        floatingMic.setGravity(Gravity.CENTER);
        floatingMic.setBackground(UiKit.brandGradient(this, 40f));
        floatingMic.setElevation(UiKit.dp(this, 10));
        FrameLayout.LayoutParams micParams = new FrameLayout.LayoutParams(
                UiKit.dp(this, 64), UiKit.dp(this, 64));
        micParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        micParams.bottomMargin = UiKit.dp(this, 62);
        floatingMic.setLayoutParams(micParams);
        floatingMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleListening();
            }
        });
        root.addView(floatingMic);

        setContentView(root);
        openTab(0);
    }

    private void setupVoice() {
        speaker = new TtsSpeaker(this);
        speaker.setListener(new TtsSpeaker.Listener() {
            @Override
            public void onStartSpeaking() {
                if (voiceScreen != null) {
                    voiceScreen.character().setState(CharacterView.State.SPEAKING);
                    voiceScreen.setStatus(getString(R.string.speaking));
                }
            }

            @Override
            public void onMouthLevel(float level) {
                if (voiceScreen != null) {
                    voiceScreen.character().setMouthLevel(level);
                }
            }

            @Override
            public void onDoneSpeaking() {
                if (voiceScreen != null) {
                    voiceScreen.character().setState(CharacterView.State.IDLE);
                    voiceScreen.setStatus("Tap the mic and talk to me");
                }
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });

        speech = new SpeechInput(this);
        speech.setLanguage(Language.fromCode(prefs.getLanguage()));
        speech.setListener(new SpeechInput.Listener() {
            @Override
            public void onReadyForSpeech() {
                setListeningUi(true);
                if (voiceScreen != null) {
                    voiceScreen.character().setState(CharacterView.State.LISTENING);
                    voiceScreen.setStatus(getString(R.string.listening));
                }
            }

            @Override
            public void onPartialResult(String text) {
                if (voiceScreen != null && text != null && !text.isEmpty()) {
                    voiceScreen.character().setMouthLevel(0.2f + (float) Math.random() * 0.3f);
                    voiceScreen.setTranscript(text);
                }
            }

            @Override
            public void onFinalResult(String text, float confidence) {
                setListeningUi(false);
                if (text == null || text.trim().isEmpty()) {
                    return;
                }
                if (chatScreen != null) {
                    chatScreen.setInput(text);
                }
                sendMessage(text, true);
            }

            @Override
            public void onError(String message) {
                setListeningUi(false);
                if (voiceScreen != null) {
                    voiceScreen.character().setState(CharacterView.State.CONFUSED);
                    voiceScreen.setStatus(message);
                }
                toast(message);
            }

            @Override
            public void onEndOfSpeech() {
                setListeningUi(false);
            }
        });
    }

    private void setListeningUi(boolean active) {
        listening = active;
        if (voiceScreen != null) {
            voiceScreen.setMicActive(active);
            if (!active) {
                voiceScreen.setStatus("Tap the mic and talk to me");
                voiceScreen.character().setState(CharacterView.State.IDLE);
            }
        }
    }

    private void requestStartupPermissions() {
        java.util.List<String> missing = new java.util.ArrayList<String>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            missing.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            boolean audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
            if (!audio) {
                toast("Voice features stay off until you allow the microphone");
            }
        }
    }

    // ---------------------------------------------------------------- tabs

    public void openTab(int index) {
        if (index < 0 || index > 5) {
            return;
        }
        currentTab = index;
        Screen screen;
        switch (index) {
            case 1:
                if (chatScreen == null) {
                    chatScreen = new ChatScreen(this);
                }
                screen = chatScreen;
                break;
            case 2:
                if (voiceScreen == null) {
                    voiceScreen = new VoiceScreen(this);
                }
                screen = voiceScreen;
                break;
            case 3:
                if (tasksScreen == null) {
                    tasksScreen = new TasksScreen(this);
                }
                screen = tasksScreen;
                break;
            case 4:
                if (settingsScreen == null) {
                    settingsScreen = new SettingsScreen(this);
                }
                screen = settingsScreen;
                break;
            case 5:
                if (memoryScreen == null) {
                    memoryScreen = new MemoryScreen(this);
                }
                screen = memoryScreen;
                break;
            default:
                if (homeScreen == null) {
                    homeScreen = new HomeScreen(this);
                }
                screen = homeScreen;
                break;
        }
        if (topBar != null) {
            topBar.setVisibility(index == 0 ? View.GONE : View.VISIBLE);
        }
        if (floatingMic != null) {
            // Home has its own composer; the mic floats over every other tab.
            floatingMic.setVisibility(index == 0 || index == 2 ? View.GONE : View.VISIBLE);
        }
        content.removeAllViews();
        content.addView(screen.build(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screen.onShown();
        buildNav();
        if (index == 1) {
            refreshFromServer();
        }
    }

    private void buildNav() {
        nav.removeAllViews();
        String[] labels = {"Home", "Chat", "Voice", "Triggers", "Settings"};
        String[] glyphs = {"⌂", "💬", "🎙", "⚡", "⚙"};
        // Memory stays one tap away through the Home card and Settings → Tools.
        int[] targets = {0, 1, 2, 3, 4};
        for (int i = 0; i < labels.length; i++) {
            final int target = targets[i];
            boolean active = target == currentTab || (currentTab == 5 && target == 0);
            LinearLayout item = UiKit.vertical(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, UiKit.dp(this, 4), 0, 0);
            TextView icon = UiKit.text(this, glyphs[i], 18f, active ? UiKit.ACCENT : UiKit.TEXT_DIM, false);
            icon.setGravity(Gravity.CENTER);
            item.addView(icon);
            TextView caption = UiKit.text(this, labels[i], 11f, active ? UiKit.ACCENT : UiKit.TEXT_DIM,
                    active);
            caption.setGravity(Gravity.CENTER);
            item.addView(caption);
            if (active) {
                View indicator = new View(this);
                android.graphics.drawable.GradientDrawable bar =
                        UiKit.rounded(UiKit.ACCENT, 2f, this);
                indicator.setBackground(bar);
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                        UiKit.dp(this, 18), UiKit.dp(this, 3));
                barParams.topMargin = UiKit.dp(this, 3);
                indicator.setLayoutParams(barParams);
                item.addView(indicator);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            item.setLayoutParams(params);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openTab(target);
                }
            });
            nav.addView(item);
        }
    }

    // ------------------------------------------------------------- chatting

    public void sendMessage(final String text, final boolean voiceMode) {
        if (text == null || text.trim().isEmpty() || streaming) {
            return;
        }
        openTab(0);
        final String message = text.trim();
        chatScreen.addBubble(message, true, false);
        chatScreen.setStatus(getString(R.string.thinking));
        if (voiceScreen != null) {
            voiceScreen.character().setState(CharacterView.State.THINKING);
            voiceScreen.setStatus(getString(R.string.thinking));
        }
        persistLocalMessage(message, true);
        streaming = true;

        final String zone = java.util.TimeZone.getDefault().getID();
        final JsonObject body = new JsonObject()
                .put("message", message)
                .put("timeZone", zone)
                .put("voiceMode", Boolean.valueOf(voiceMode))
                .put("personaId", prefs.getPersona())
                .put("brainId", prefs.getBrain())
                .put("languageCode", prefs.getLanguage())
                .put("provider", prefs.getProvider())
                .put("model", prefs.getModel());
        if (conversationId != null) {
            body.put("conversationId", conversationId);
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final StringBuilder buffer = new StringBuilder();
                final boolean[] firstToken = {true};
                api.streamMessage(body, new ApiClient.StreamListener() {
                    @Override
                    public void onMeta(JsonObject meta) {
                        String id = meta.getString("conversationId", null);
                        if (id != null) {
                            conversationId = id;
                            store.putJson("lastConversation", new JsonObject().put("id", id));
                        }
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                chatScreen.setStatus(getString(R.string.speaking));
                            }
                        });
                    }

                    @Override
                    public void onToken(String delta) {
                        buffer.append(delta);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (firstToken[0]) {
                                    firstToken[0] = false;
                                    chatScreen.addBubble("", false, false);
                                    if (voiceScreen != null) {
                                        voiceScreen.character().setState(CharacterView.State.SPEAKING);
                                    }
                                }
                                chatScreen.updateLastAssistantBubble(buffer.toString());
                                if (voiceScreen != null) {
                                    voiceScreen.character().pulseMouth();
                                }
                            }
                        });
                    }

                    @Override
                    public void onDone(JsonObject result) {
                        final String reply = result.has("text") ? result.getString("text") : buffer.toString();
                        final boolean shouldSpeak = result.getBoolean("shouldSpeak", prefs.isAutoSpeak());
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                finishReply(reply, shouldSpeak, message, voiceMode);
                            }
                        });
                    }

                    @Override
                    public void onError(final String error) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                streaming = false;
                                chatScreen.setStatus("");
                                chatScreen.addBubble("I couldn't reach the server just now. " + error
                                        + "\n\nCheck the server address in Settings, then try again. "
                                        + "I can still answer simpler things offline.", false, false);
                                if (voiceScreen != null) {
                                    voiceScreen.character().setState(CharacterView.State.CONFUSED);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private void finishReply(String reply, boolean shouldSpeak, String userMessage, boolean voiceMode) {
        streaming = false;
        String clean = reply == null || reply.trim().isEmpty()
                ? "I'm here - could you say that again?" : reply;
        if (chatScreen != null) {
            chatScreen.updateLastAssistantBubble(clean);
            chatScreen.setStatus("");
        }
        persistLocalMessage(clean, false);
        if (voiceScreen != null) {
            voiceScreen.character().setState(CharacterView.State.IDLE);
            voiceScreen.setStatus("Tap the mic and talk to me");
            voiceScreen.setTranscript("");
        }
        if (shouldSpeak && (voiceMode || prefs.isAutoSpeak())) {
            speak(clean);
        }
        handleDeviceIntent(userMessage, clean);
    }

    /** Confirmation-gated device actions, driven by the shared intent classifier. */
    private void handleDeviceIntent(String userMessage, String reply) {
        IntentResult intent = IntentClassifier.classify(userMessage, System.currentTimeMillis(),
                java.util.TimeZone.getDefault().getID());
        switch (intent.getIntent()) {
            case OPEN_APP: {
                final String app = intent.slot("app");
                if (app != null && !app.isEmpty()) {
                    confirm("Open " + app + "?", "INDRA can open installed apps for you.",
                            new Runnable() {
                                @Override
                                public void run() {
                                    AppControl.Result result = AppControl.openApp(MainActivity.this, app);
                                    toast(result.message);
                                }
                            });
                }
                break;
            }
            case CALL: {
                final String number = resolveRecipient(intent.slot("recipient"));
                if (number != null && !number.isEmpty()) {
                    confirm("Call " + number + "?", "This places a phone call.", new Runnable() {
                        @Override
                        public void run() {
                            toast(AppControl.call(MainActivity.this, number).message);
                        }
                    });
                }
                break;
            }
            case SEND_SMS: {
                final String number = resolveRecipient(intent.slot("recipient"));
                final String body = intent.slot("body");
                if (number != null && !number.isEmpty()) {
                    confirm("Send a message to " + number + "?",
                            body == null ? "Message text will be filled in." : body, new Runnable() {
                                @Override
                                public void run() {
                                    toast(AppControl.sendSms(MainActivity.this, number, body).message);
                                }
                            });
                }
                break;
            }
            case WHATSAPP: {
                final String number = resolveRecipient(intent.slot("recipient"));
                final String body = intent.slot("body");
                confirm("Open WhatsApp?", "INDRA opens WhatsApp with your draft - you press send.",
                        new Runnable() {
                            @Override
                            public void run() {
                                toast(AppControl.whatsapp(MainActivity.this, number, body).message);
                            }
                        });
                break;
            }
            case OPEN_URL: {
                final String url = intent.slot("url");
                if (url != null && !url.isEmpty()) {
                    toast(AppControl.openUrl(this, url).message);
                }
                break;
            }
            case WHITEBOARD:
                showWhiteboard();
                break;
            case SETTINGS_OPEN:
                openTab(4);
                break;
            case VOICE_STOP:
                stopSpeaking();
                break;
            case REMINDER_CREATE:
            case TASK_CREATE: {
                long at = intent.slotLong("at", 0L);
                String title = intent.slot("title");
                if (at > 0L && title != null && !title.isEmpty()) {
                    offerReminder(title, at);
                }
                break;
            }
            case SYSTEM_INFO:
                reportBattery();
                break;
            default:
                // Pure conversation: reply already shown.
                break;
        }
    }

    /** Turns a spoken name into a phone number using the contacts permission. */
    private String resolveRecipient(String spoken) {
        if (spoken == null || spoken.trim().isEmpty()) {
            return null;
        }
        String cleaned = spoken.trim();
        if (ai.indra.android.platform.Contacts.looksLikeNumber(cleaned)) {
            return cleaned.replaceAll("[^+0-9]", "");
        }
        if (!ai.indra.android.platform.Contacts.hasPermission(this)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS},
                    REQ_PERMISSIONS);
            toast("Allow contacts access so I can find " + cleaned);
            return null;
        }
        ai.indra.android.platform.Contacts.Match match =
                ai.indra.android.platform.Contacts.find(this, cleaned);
        if (match == null) {
            toast("I couldn't find " + cleaned + " in your contacts");
            return null;
        }
        return match.numbers.get(0).replaceAll("[^+0-9]", "");
    }

    private void offerReminder(final String title, final long at) {
        String when = ai.indra.core.util.Times.toDisplay(at, java.util.TimeZone.getDefault().getID());
        confirm("Set a reminder?", "\"" + title + "\" at " + when, new Runnable() {
            @Override
            public void run() {
                JsonObject body = new JsonObject()
                        .put("title", title)
                        .put("remindAt", Long.valueOf(at))
                        .put("timeZone", java.util.TimeZone.getDefault().getID());
                createTask(body);
            }
        });
    }

    private void reportBattery() {
        JsonObject info = AppControl.systemInfo(this);
        String text = "Battery is at " + info.getInt("batteryPercent", -1) + "%"
                + (info.getBoolean("charging", false) ? " and charging" : "")
                + ". " + info.getString("device", "") + " on Android "
                + info.getString("androidVersion", "") + ".";
        if (chatScreen != null) {
            chatScreen.addBubble(text, false, false);
        }
        speak(text);
    }

    private void persistLocalMessage(String text, boolean fromUser) {
        ChatMessage message = ChatMessage.of(conversationId == null ? "local" : conversationId,
                "local-user", fromUser ? MessageRole.USER : MessageRole.ASSISTANT, text);
        store.saveMessage(message);
    }

    // -------------------------------------------------------------- voice

    public void toggleListening() {
        if (listening) {
            speech.stop();
            setListeningUi(false);
            return;
        }
        if (!SpeechInput.hasPermission(this)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_PERMISSIONS);
            toast("Allow the microphone to talk to Indra");
            return;
        }
        speaker.stop();
        speech.setLanguage(Language.fromCode(prefs.getLanguage()));
        speech.start();
    }

    public void speak(String text) {
        Language language = speaker.detectLanguage(text, Language.fromCode(prefs.getLanguage()));
        speaker.apply(language, prefs.getSpeechRate(), prefs.getSpeechPitch());
        speaker.speak(text, language);
        if (voiceScreen != null) {
            voiceScreen.character().setState(CharacterView.State.SPEAKING);
        }
    }

    public void stopSpeaking() {
        speaker.stop();
    }

    public void toggleLanguage() {
        String next = "en".equalsIgnoreCase(prefs.getLanguage()) ? "ne" : "en";
        prefs.setLocalSettings(prefs.getPersona(), prefs.getBrain(), next, prefs.getTheme(),
                prefs.getProvider(), prefs.getModel(), prefs.isMemoryEnabled(), prefs.isAutoMemory(),
                prefs.isAutoSpeak(), prefs.getSpeechRate(), prefs.getSpeechPitch());
        speech.setLanguage(Language.fromCode(next));
        session().pushSettings();
        toast("nepali".equals(next) ? "भाषा नेपालीमा परिवर्तन भयो" : "Language set to English");
        reloadSettings();
    }

    public void toggleWakeWord() {
        boolean enable = !prefs.isWakeWordEnabled();
        prefs.setWakeWordEnabled(enable);
        session().pushSettings();
        Intent service = new Intent(this, WakeWordService.class);
        if (enable) {
            service.setAction(WakeWordService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            toast("Hands-free is on - say \"" + prefs.getWakeWord() + "\"");
        } else {
            service.setAction(WakeWordService.ACTION_STOP);
            startService(service);
            toast("Hands-free listening is off");
        }
    }

    // ------------------------------------------------------------- memory

    public void createMemory(final String content, final String category) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        final MemoryItem item = MemoryItem.create(userId(), content.trim(),
                MemoryCategory.fromName(category), MemorySource.USER_STATED, 3);
        store.saveMemories(mergeMemories(item));
        if (memoryScreen != null) {
            memoryScreen.reload();
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.createMemory(content.trim(), category, 3);
                    toastOnUi("Saved to memory");
                } catch (ApiClient.ApiException error) {
                    toastOnUi("Saved on this phone; sync failed (" + error.getMessage() + ")");
                }
            }
        });
    }

    public void updateMemory(final String id, final String content, final String category) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        List<MemoryItem> items = store.memories();
        for (MemoryItem item : items) {
            if (item.getId().equals(id)) {
                item.setContent(content.trim());
                item.setCategory(MemoryCategory.fromName(category));
                item.setUpdatedAt(System.currentTimeMillis());
            }
        }
        store.saveMemories(items);
        if (memoryScreen != null) {
            memoryScreen.reload();
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.updateMemory(id, new JsonObject().put("content", content.trim())
                            .put("category", category));
                } catch (ApiClient.ApiException error) {
                    toastOnUi("Updated locally; sync failed");
                }
            }
        });
    }

    public void deleteMemory(final String id) {
        store.deleteMemory(id);
        if (memoryScreen != null) {
            memoryScreen.reload();
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.deleteMemory(id);
                } catch (ApiClient.ApiException error) {
                    toastOnUi("Deleted locally; sync failed");
                }
            }
        });
    }

    public void clearMemories() {
        store.clearMemories();
        if (memoryScreen != null) {
            memoryScreen.reload();
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.clearMemories();
                    toastOnUi("All memories deleted");
                } catch (ApiClient.ApiException error) {
                    toastOnUi("Cleared on this phone; sync failed");
                }
            }
        });
    }

    private List<MemoryItem> mergeMemories(MemoryItem item) {
        List<MemoryItem> items = store.memories();
        items.add(item);
        return items;
    }

    // -------------------------------------------------------------- tasks

    public void createTask(final JsonObject body) {
        final String zone = body.getString("timeZone", java.util.TimeZone.getDefault().getID());
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonObject response = api.createTask(body);
                    JsonObject taskJson = response.getObjectOrEmpty("task");
                    final TaskItem task = TaskItem.fromJson(taskJson);
                    store.saveTask(task);
                    if (task.isReminder() && ReminderScheduler.canScheduleExact(MainActivity.this)) {
                        ReminderScheduler.schedule(MainActivity.this, task, zone);
                    } else if (task.isReminder()) {
                        ReminderScheduler.schedule(MainActivity.this, task, zone);
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("Task created: " + task.getTitle());
                            if (tasksScreen != null) {
                                tasksScreen.reload();
                            }
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    // Offline: keep it locally so nothing is lost.
                    TaskItem local = TaskItem.create(userId(), body.getString("title", "Task"));
                    local.setRemindAt(body.getLong("remindAt", 0L));
                    local.setDueAt(body.getLong("remindAt", 0L));
                    local.setRecurrence(ai.indra.core.model.Recurrence.fromId(
                            body.getString("recurrence", "none")));
                    store.saveTask(local);
                    ReminderScheduler.schedule(MainActivity.this, local, zone);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("Saved offline - it will sync with the next connection");
                            if (tasksScreen != null) {
                                tasksScreen.reload();
                            }
                        }
                    });
                }
            }
        });
    }

    public void completeTask(final String id) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.completeTask(id, java.util.TimeZone.getDefault().getID());
                } catch (ApiClient.ApiException error) {
                    Log.warn("MainActivity", "Complete task failed: " + error.getMessage());
                }
                ReminderScheduler.cancel(MainActivity.this, id);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (tasksScreen != null) {
                            tasksScreen.reload();
                        }
                    }
                });
            }
        });
    }

    public void deleteTask(final String id) {
        store.deleteTask(id);
        ReminderScheduler.cancel(this, id);
        if (tasksScreen != null) {
            tasksScreen.reload();
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.deleteTask(id);
                } catch (ApiClient.ApiException error) {
                    toastOnUi("Deleted locally; sync failed");
                }
            }
        });
    }

    // ------------------------------------------------------------ settings

    public void reloadSettings() {
        openTab(4);
    }

    public void removeApiKey(final String id) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.removeKey(id);
                    toastOnUi("Key removed");
                } catch (ApiClient.ApiException error) {
                    toastOnUi("Could not remove key: " + error.getMessage());
                }
            }
        });
    }

    public void addApiKey(final String provider, final String label, final String key, final boolean permanent) {
        if (key.trim().isEmpty()) {
            toast("Paste the key first");
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.addKey(provider, label, key, permanent);
                    toastOnUi(provider + " key saved");
                } catch (ApiClient.ApiException error) {
                    toastOnUi("Could not save key: " + error.getMessage());
                }
            }
        });
    }

    public void resetApiKeys() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.resetKeys(null);
                    toastOnUi("All keys removed");
                } catch (ApiClient.ApiException error) {
                    toastOnUi("Reset failed: " + error.getMessage());
                }
            }
        });
    }

    public void activateLicense(final String key) {
        if (key.trim().isEmpty()) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonObject response = api.activateLicense(key.trim());
                    prefs.setLicenseKey(key.trim());
                    toastOnUi(response.getString("message", "Licence activated"));
                } catch (ApiClient.ApiException error) {
                    toastOnUi("Licence error: " + error.getMessage());
                }
            }
        });
    }

    public void startPairing() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final JsonObject response = api.startPairing(prefs.getDeviceName(), "ANDROID");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String code = response.getString("code", response.getString("pairingCode", ""));
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Pairing code")
                                    .setMessage("Enter this code on your Windows app within 10 minutes:\n\n"
                                            + code + "\n\nOnly share it with yourself.")
                                    .setPositiveButton("Done", null).show();
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    toastOnUi("Pairing failed: " + error.getMessage());
                }
            }
        });
    }

    public void revokeDevice(final String id) {
        confirm("Revoke this device?", "It will need to pair again to sync.",
                new Runnable() {
                    @Override
                    public void run() {
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    api.revokeDevice(id);
                                    toastOnUi("Device revoked");
                                } catch (ApiClient.ApiException error) {
                                    toastOnUi("Could not revoke: " + error.getMessage());
                                }
                            }
                        });
                    }
                });
    }

    public void syncNow() {
        connectionStatus.setText("Syncing…");
        final String zone = java.util.TimeZone.getDefault().getID();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonObject memories = api.memories();
                    JsonArray array = memories.getArray("memories");
                    List<MemoryItem> items = new java.util.ArrayList<MemoryItem>();
                    if (array != null) {
                        for (int i = 0; i < array.size(); i++) {
                            JsonObject memory = array.getObject(i);
                            if (memory != null) {
                                items.add(MemoryItem.fromJson(memory));
                            }
                        }
                    }
                    store.saveMemories(items);

                    JsonObject tasks = api.tasks(false);
                    JsonArray taskArray = tasks.getArray("tasks");
                    if (taskArray != null) {
                        for (int i = 0; i < taskArray.size(); i++) {
                            TaskItem task = TaskItem.fromJson(taskArray.getObject(i));
                            store.saveTask(task);
                            if (task.isReminder()) {
                                ReminderScheduler.schedule(MainActivity.this, task, zone);
                            }
                        }
                    }

                    JsonObject profile = session.refreshProfile();
                    final int memoryCount = items.size();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatus.setText("Synced · " + memoryCount + " memories");
                            if (memoryScreen != null) {
                                memoryScreen.reload();
                            }
                            if (tasksScreen != null) {
                                tasksScreen.reload();
                            }
                            if (profile == null) {
                                toast("Signed in, but the profile refresh failed");
                            }
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatus.setText("Offline - showing saved data");
                            toast("Sync failed: " + error.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void refreshFromServer() {
        if (!prefs.hasSession()) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final JsonObject profile = session.refreshProfile();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (profile != null) {
                                connectionStatus.setText("Online · secure session");
                                if (settingsScreen != null && currentTab == 4) {
                                    openTab(4);
                                }
                            } else {
                                connectionStatus.setText("Offline - showing saved data");
                            }
                        }
                    });
                } catch (RuntimeException error) {
                    Log.warn("MainActivity", "Refresh failed: " + error.getMessage());
                }
            }
        });
    }

    public void exportData() {
        final JsonObject data = new JsonObject();
        data.put("exportedAt", Long.valueOf(System.currentTimeMillis()));
        data.put("app", "INDRA AI");
        data.put("version", BuildConfig.VERSION_NAME);
        JsonArray memories = new JsonArray();
        for (MemoryItem item : store.memories()) {
            memories.add(item.toJson());
        }
        data.put("memories", memories);
        JsonArray tasks = new JsonArray();
        for (TaskItem item : store.tasks(true)) {
            tasks.add(item.toJson());
        }
        data.put("tasks", tasks);
        final File file = FileExport.exportJson(this, "indra-export", data);
        if (file == null) {
            toast("Export failed");
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Export ready")
                    .setMessage("Your data was written to a private app file.\n\n" + file.getName())
                    .setPositiveButton("Share", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FileExport.shareFile(MainActivity.this, file, "application/json");
                        }
                    })
                    .setNegativeButton("Close", null).show();
        }
    }

    public void clearHistory() {
        store.clearMessages();
        conversationId = null;
        store.putJson("lastConversation", new JsonObject());
        if (chatScreen != null) {
            chatScreen.reload();
        }
        toast("History cleared on this device");
    }

    public void showWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERMISSIONS);
            toast("Allow location for local weather - or ask me for a city by name");
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    android.location.LocationManager manager =
                            (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
                    android.location.Location last = null;
                    if (manager != null) {
                        last = manager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                        if (last == null) {
                            last = manager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                        }
                    }
                    Double lat = last == null ? null : Double.valueOf(last.getLatitude());
                    Double lon = last == null ? null : Double.valueOf(last.getLongitude());
                    final JsonObject weather = api.weather(null, lat, lon);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String summary = weather.getString("summary",
                                    weather.getString("description", "Weather data received"));
                            openTab(0);
                            if (chatScreen != null) {
                                chatScreen.addBubble(summary, false, false);
                            }
                            speak(summary);
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    toastOnUi("Weather is unavailable right now: " + error.getMessage());
                }
            }
        });
    }

    public void showHistory() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final JsonObject response = api.conversations();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showConversationPicker(response.getArray("conversations"));
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    toastOnUi("Could not load history: " + error.getMessage());
                }
            }
        });
    }

    private void showConversationPicker(JsonArray conversations) {
        if (conversations == null || conversations.size() == 0) {
            toast("No conversations yet");
            return;
        }
        final String[] ids = new String[conversations.size()];
        final String[] titles = new String[conversations.size()];
        for (int i = 0; i < conversations.size(); i++) {
            JsonObject conversation = conversations.getObject(i);
            ids[i] = conversation.getString("id", "");
            titles[i] = conversation.getString("title", "Conversation") + " · "
                    + ai.indra.core.util.Times.toDisplay(conversation.getLong("updatedAt",
                    System.currentTimeMillis()), java.util.TimeZone.getDefault().getID());
        }
        new AlertDialog.Builder(this).setTitle(getString(R.string.history_title))
                .setItems(titles, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadConversation(ids[which]);
                    }
                }).show();
    }

    private void loadConversation(final String id) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonObject response = api.messages(id);
                    JsonArray array = response.getArray("messages");
                    if (array != null) {
                        for (int i = 0; i < array.size(); i++) {
                            store.saveMessage(ChatMessage.fromJson(array.getObject(i)));
                        }
                    }
                    conversationId = id;
                    store.putJson("lastConversation", new JsonObject().put("id", id));
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            openTab(0);
                            if (chatScreen != null) {
                                chatScreen.reload();
                            }
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    toastOnUi("Could not open conversation: " + error.getMessage());
                }
            }
        });
    }

    public void showWhiteboard() {
        startActivity(new Intent(this, WhiteboardActivity.class));
    }

    public void openAdmin() {
        if (!session.isAdmin()) {
            toast("This account does not have administrator access");
            return;
        }
        startActivity(new Intent(this, AdminActivity.class));
    }

    public void signOut() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setMessage("Your memories stay on the server for your next sign-in. "
                        + "This device will forget its session tokens.")
                .setPositiveButton("Sign out", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        session.logout();
                        store.clearMessages();
                        stopService(new Intent(MainActivity.this, WakeWordService.class));
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void handleVoiceIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String command = intent.getStringExtra("voiceCommand");
        boolean fromWake = intent.getBooleanExtra("fromWakeWord", false);
        if (fromWake && command != null && !command.trim().isEmpty()) {
            String normalized = command.trim();
            if (WakeWord.isBareWakeWord(normalized, prefs.getWakeWord())) {
                openTab(1);
                toggleListening();
            } else {
                sendMessage(normalized, true);
            }
        }
    }

    // -------------------------------------------------------------- helpers

    public void confirm(String title, String message, final Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        action.run();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    public void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    public void toastOnUi(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                toast(message);
            }
        });
    }

    public void runOnUi(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public ExecutorService executor() {
        return executor;
    }

    /** Opens the sign-in / sign-up screen even when a device session exists. */
    public void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_FORCE_SHOW, true);
        startActivity(intent);
    }

    /** AI Aura picker: six holographic looks, saved per device. */
    public void showAuraPicker() {
        final AuraStyle[] styles = AuraStyle.values();
        String[] labels = AuraStyle.labels();
        int selected = AuraStyle.fromId(prefs.getAuraStyle()).ordinal();
        new AlertDialog.Builder(this)
                .setTitle("AI Aura")
                .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AuraStyle style = styles[which];
                        prefs.setAuraStyle(style.name());
                        dialog.dismiss();
                        toast("Aura set to " + style.getLabel());
                        openTab(currentTab);
                    }
                })
                .setNeutralButton("Animation speed", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showAuraSpeed();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showAuraSpeed() {
        final String[] labels = {"Calm (0.6x)", "Balanced (1.0x)", "Lively (1.5x)", "Cinematic (2.0x)"};
        final float[] speeds = {0.6f, 1.0f, 1.5f, 2.0f};
        new AlertDialog.Builder(this)
                .setTitle("Orb animation speed")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.setAuraSpeed(speeds[which]);
                        toast("Animation speed set");
                    }
                })
                .show();
    }

    /** Notification panel: upcoming reminders plus the system notification settings. */
    public void showNotifications() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final LinearLayout sheet = UiKit.vertical(this);
        sheet.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 8), UiKit.dp(this, 18), 0);
        sheet.addView(UiKit.subtitle(this, "Loading your reminders…"));
        builder.setTitle("Notifications").setView(UiKit.scroll(this, sheet))
                .setPositiveButton("System settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openNotificationSettings();
                    }
                })
                .setNegativeButton("Close", null);
        final AlertDialog dialog = builder.show();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final JsonObject response = api.tasks(false);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            sheet.removeAllViews();
                            JsonArray tasks = response.getArrayOrEmpty("tasks");
                            SimpleDateFormat format = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault());
                            int shown = 0;
                            for (int i = 0; i < tasks.size() && shown < 6; i++) {
                                JsonObject task = tasks.getObject(i);
                                long when = task.getLong("remindAt", 0L);
                                if (when <= 0L) {
                                    when = task.getLong("dueAt", 0L);
                                }
                                if (when <= 0L || task.getBoolean("completed", false)) {
                                    continue;
                                }
                                LinearLayout row = card();
                                row.setOrientation(LinearLayout.HORIZONTAL);
                                LinearLayout texts = UiKit.vertical(MainActivity.this);
                                texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                                texts.addView(UiKit.text(MainActivity.this,
                                        task.getString("title", "Reminder"), 14f, UiKit.TEXT, false));
                                texts.addView(UiKit.text(MainActivity.this,
                                        format.format(new Date(when)), 12f, UiKit.TEXT_DIM, false));
                                row.addView(texts);
                                sheet.addView(row);
                                shown++;
                            }
                            if (shown == 0) {
                                sheet.addView(UiKit.bodyText(MainActivity.this,
                                        "No upcoming reminders. Ask INDRA to remind you, or use Triggers."));
                            }
                            sheet.addView(UiKit.bodyText(MainActivity.this,
                                    "Call announcements and chat notifications can be enabled in Settings."));
                        }
                    });
                } catch (final ApiClient.ApiException error) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            sheet.removeAllViews();
                            sheet.addView(UiKit.bodyText(MainActivity.this,
                                    "Cannot load reminders: " + error.getMessage()));
                        }
                    });
                }
            }
        });
    }

    private LinearLayout card() {
        return UiKit.card(this);
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName()));
        }
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            toast("This device has no notification settings screen");
        }
    }

    private void applySystemBarAppearance() {
        boolean dark = prefs.isDarkTheme();
        getWindow().setStatusBarColor(UiKit.BG);
        getWindow().setNavigationBarColor(UiKit.SURFACE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (dark) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    /** Switches the light/dark palette and rebuilds the shell. */
    public void applyTheme(String theme) {
        prefs.setTheme(theme);
        UiKit.applyTheme(prefs.isDarkTheme());
        applySystemBarAppearance();
        homeScreen = null;
        chatScreen = null;
        voiceScreen = null;
        memoryScreen = null;
        tasksScreen = null;
        settingsScreen = null;
        buildShell();
    }

    public Prefs prefs() {
        return prefs;
    }

    public LocalStore store() {
        return store;
    }

    public ApiClient api() {
        return api;
    }

    public SessionManager session() {
        return session;
    }

    public String currentConversationId() {
        return conversationId;
    }

    private String userId() {
        JsonObject user = prefs.getUser();
        String id = user == null ? null : user.getString("id", null);
        return id == null ? "local-user" : id;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.hasSession()) {
            syncNow();
        }
    }

    @Override
    protected void onDestroy() {
        if (speaker != null) {
            speaker.shutdown();
        }
        if (speech != null) {
            speech.destroy();
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}
