package ai.indra.desktop;

import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.BrainProfile;
import ai.indra.core.model.Persona;
import ai.indra.core.util.Log;
import ai.indra.core.util.Times;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * INDRA AI for Windows: dashboard, chat, animated character, voice, memory,
 * history, file tools, PC control and settings - talking to the same backend as
 * the Android app.
 */
public class DesktopApp extends Application {

    private static final String ACCENT = "#5B8CFF";

    private DesktopConfig config;
    private DesktopApi api;
    private BackendManager backend;
    private DesktopVoice voice;
    private ExecutorService executor;
    private Stage stage;

    private BorderPane root;
    private StackPane content;
    private Label statusLabel;
    private VBox chatMessages;
    private ScrollPane chatScroll;
    private CharacterCanvas character;
    private String conversationId;
    private boolean busy;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        config = new DesktopConfig();
        api = new DesktopApi(config);
        backend = new BackendManager(config);
        voice = new DesktopVoice(config);
        executor = Executors.newFixedThreadPool(3);

        primaryStage.setTitle("INDRA AI - personal assistant by Umesh Chaudhary");
        primaryStage.setMinWidth(980);
        primaryStage.setMinHeight(680);
        if (config.hasSession()) {
            showMain();
        } else {
            showLogin();
        }
        primaryStage.show();
    }

    @Override
    public void stop() {
        executor.shutdownNow();
        if (character != null) {
            character.dispose();
        }
    }

    // ------------------------------------------------------------------ login

    private void showLogin() {
        VBox column = new VBox(12);
        column.setPadding(new Insets(28, 40, 28, 40));
        column.setAlignment(Pos.CENTER);
        column.setStyle("-fx-background-color: #080B18;");

        CharacterCanvas avatar = new CharacterCanvas(260, 260);
        avatar.setState(CharacterCanvas.State.HAPPY);
        column.getChildren().add(avatar);

        Label title = new Label("INDRA AI");
        title.setStyle("-fx-font-size: 30px; -fx-text-fill: #E9EDFF; -fx-font-weight: bold;");
        Label subtitle = new Label("Your personal AI assistant - created by Umesh Chaudhary");
        subtitle.setStyle("-fx-text-fill: #9AA3C7;");
        column.getChildren().addAll(title, subtitle);

        Label serverLabel = new Label("Backend address");
        serverLabel.setStyle("-fx-text-fill: #9AA3C7;");
        TextField server = field(config.getBaseUrl());
        HBox serverRow = new HBox(8, server);
        HBox.setHgrow(server, Priority.ALWAYS);
        Button startBackend = button("Start backend", false);
        startBackend.setOnAction(event -> statusUpdate(backend.start()));
        serverRow.getChildren().add(startBackend);

        TextField email = field("you@example.com");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.setStyle(fieldStyle());
        TextField displayName = field("Your name (for a new account)");

        Label message = new Label();
        message.setStyle("-fx-text-fill: #FF5C7A; -fx-wrap-text: true;");

        Button signIn = button("Sign in", true);
        Button register = button("Create account", false);
        Button google = button("Continue with Google", false);
        Button github = button("Continue with GitHub", false);

        // Keep the API pointed at whatever the user typed.
        server.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.startsWith("http")) {
                config.setBaseUrl(newValue);
            }
        });

        signIn.setOnAction(event -> runAsync(() -> {
            try {
                api.login(email.getText().trim(), password.getText());
                Platform.runLater(() -> {
                    refreshStatus();
                    showMain();
                });
            } catch (DesktopApi.ApiException error) {
                showError(message, error);
            }
        }));

        register.setOnAction(event -> runAsync(() -> {
            try {
                api.register(email.getText().trim(), password.getText(), displayName.getText().trim());
                Platform.runLater(() -> {
                    refreshStatus();
                    showMain();
                });
            } catch (DesktopApi.ApiException error) {
                showError(message, error);
            }
        }));

        google.setOnAction(event -> startOauth("google", message));
        github.setOnAction(event -> startOauth("github", message));

        Label hint = new Label("Google and GitHub sign-in open your browser. Paste the address you land on "
                + "back here to finish. They require client IDs configured on the server "
                + "(config/backend.env.example).");
        hint.setStyle("-fx-text-fill: #6E7799; -fx-wrap-text: true;");
        hint.setMaxWidth(520);

        column.getChildren().addAll(serverLabel, serverRow, email, password, displayName,
                signIn, register, google, github, message, hint);

        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #080B18;");
        stage.setScene(new Scene(scroll, 640, 760));
    }

    private void startOauth(String provider, Label message) {
        String redirect = "http://127.0.0.1:8421/oauth2redirect";
        runAsync(() -> {
            try {
                String verifier = "desktop-" + java.util.UUID.randomUUID().toString().replace("-", "");
                String state = java.util.UUID.randomUUID().toString();
                JsonObject start = api.oauthStart(provider, state, redirect, verifier);
                String url = start.getString("authorizeUrl", start.getString("url", ""));
                if (url.isEmpty()) {
                    Platform.runLater(() -> message.setText(provider
                            + " sign-in is not configured on the server yet."));
                    return;
                }
                Platform.runLater(() -> {
                    PcControl.openUrl(url);
                    TextInputDialog dialog = new TextInputDialog();
                    dialog.setTitle("Finish " + provider + " sign-in");
                    dialog.setHeaderText("Paste the full address your browser was redirected to");
                    dialog.setContentText("Redirect URL:");
                    Optional<String> pasted = dialog.showAndWait();
                    if (!pasted.isPresent()) {
                        return;
                    }
                    String code = extractQuery(pasted.get(), "code");
                    if (code == null) {
                        message.setText("That address did not contain a sign-in code.");
                        return;
                    }
                    runAsync(() -> {
                        try {
                            api.oauthExchange(provider, code, redirect, verifier);
                            Platform.runLater(this::showMain);
                        } catch (DesktopApi.ApiException error) {
                            Platform.runLater(() -> message.setText("Sign-in failed: " + error.getMessage()));
                        }
                    });
                });
            } catch (DesktopApi.ApiException error) {
                Platform.runLater(() -> message.setText("Cannot start sign-in: " + error.getMessage()));
            }
        });
    }

    private static String extractQuery(String url, String key) {
        int index = url.indexOf(key + "=");
        if (index < 0) {
            return null;
        }
        String tail = url.substring(index + key.length() + 1);
        int amp = tail.indexOf('&');
        return amp >= 0 ? tail.substring(0, amp) : tail;
    }

    // ------------------------------------------------------------------- shell

    private void showMain() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #080B18;");

        HBox top = new HBox(10);
        top.setPadding(new Insets(12));
        top.setStyle("-fx-background-color: #111629;");
        Label brand = new Label("INDRA AI");
        brand.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 17px; -fx-font-weight: bold;");
        Label creator = new Label("by Umesh Chaudhary");
        creator.setStyle("-fx-text-fill: #6E7799;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button minimize = button("−", false);
        minimize.setOnAction(event -> stage.setIconified(true));
        Button maximize = button("□", false);
        maximize.setOnAction(event -> stage.setMaximized(!stage.isMaximized()));
        Button close = button("✕", false);
        close.setOnAction(event -> {
            if (confirm("Close INDRA AI?", "The backend keeps running if you started it from here.")) {
                Platform.exit();
            }
        });
        top.getChildren().addAll(brand, creator, spacer, minimize, maximize, close);

        VBox nav = new VBox(6);
        nav.setPadding(new Insets(12));
        nav.setPrefWidth(190);
        nav.setStyle("-fx-background-color: #0C1020;");
        String[] items = {"Dashboard", "Chat", "Character", "Voice", "Memory", "History", "Files", "Settings"};
        for (final String item : items) {
            Button navButton = new Button(item);
            navButton.setMaxWidth(Double.MAX_VALUE);
            navButton.setAlignment(Pos.CENTER_LEFT);
            navButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #C8D1F0; -fx-font-size: 14px;");
            navButton.setOnAction(event -> {
                switch (item) {
                    case "Chat":
                        showChat();
                        break;
                    case "Character":
                        showCharacter();
                        break;
                    case "Voice":
                        showVoice();
                        break;
                    case "Memory":
                        showMemory();
                        break;
                    case "History":
                        showHistory();
                        break;
                    case "Files":
                        showFiles();
                        break;
                    case "Settings":
                        showSettings();
                        break;
                    default:
                        showDashboard();
                        break;
                }
            });
            nav.getChildren().add(navButton);
        }

        content = new StackPane();
        content.setStyle("-fx-background-color: #080B18;");

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #9AA3C7;");
        HBox statusBar = new HBox(12, statusLabel);
        statusBar.setPadding(new Insets(8, 12, 8, 12));
        statusBar.setStyle("-fx-background-color: #111629;");

        root.setTop(top);
        root.setLeft(nav);
        root.setCenter(content);
        root.setBottom(statusBar);

        stage.setScene(new Scene(root, 1100, 760));
        showDashboard();
        refreshStatus();
    }

    private void refreshStatus() {
        runAsync(() -> {
            boolean online = backend.healthCheck(api);
            String who = "";
            try {
                JsonObject me = api.me();
                who = me.getObjectOrEmpty("user").getString("email", "");
            } catch (DesktopApi.ApiException error) {
                who = "";
            }
            final String account = who;
            Platform.runLater(() -> {
                statusLabel.setText((online ? "Backend online" : "Backend unreachable")
                        + (account.isEmpty() ? "" : " · signed in as " + account)
                        + " · " + config.getBaseUrl());
                statusLabel.setStyle("-fx-text-fill: " + (online ? "#3DD68C" : "#FFB84D") + ";");
            });
        });
    }

    private void statusUpdate(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    // --------------------------------------------------------------- dashboard

    private void showDashboard() {
        VBox column = new VBox(12);
        column.setPadding(new Insets(18));

        Label title = new Label("Dashboard");
        title.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 22px; -fx-font-weight: bold;");
        column.getChildren().add(title);

        Label status = new Label("Checking server…");
        status.setStyle("-fx-text-fill: #9AA3C7;");
        column.getChildren().add(card(new Label("Server status"), status));

        Label system = new Label();
        system.setStyle("-fx-text-fill: #C8D1F0; -fx-wrap-text: true;");
        JsonObject info = PcControl.systemInfo();
        StringBuilder systemText = new StringBuilder();
        systemText.append(info.getString("os", "Windows")).append('\n')
                .append("Java ").append(info.getString("java", "")).append('\n')
                .append("Free disk: ").append(info.getLong("diskFreeGb", 0)).append(" GB of ")
                .append(info.getLong("diskTotalGb", 0)).append(" GB\n");
        if (info.has("battery")) {
            systemText.append("Battery: ").append(info.getString("battery", "")).append('\n');
        }
        systemText.append("Device: ").append(config.getDeviceName());
        system.setText(systemText.toString());
        column.getChildren().add(card(new Label("This PC"), system));

        HBox quick = new HBox(8);
        quick.getChildren().addAll(
                actionButton("Open Notepad", () -> confirmed("Open Notepad?",
                        () -> statusUpdate(PcControl.openApp("notepad").message))),
                actionButton("Open Documents", () -> statusUpdate(PcControl.openFolder("documents").message)),
                actionButton("Open browser", () -> statusUpdate(PcControl.openUrl("https://www.google.com").message)),
                actionButton("Lock PC", () -> confirmed("Lock this PC now?", () -> {
                    statusUpdate(PcControl.lockWorkstation().message);
                })));
        column.getChildren().add(card(new Label("Quick PC actions"), quick));

        VBox tasks = new VBox(6);
        tasks.setStyle("-fx-text-fill: #C8D1F0;");
        column.getChildren().add(card(new Label("Your tasks"), tasks));

        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #080B18;");
        content.getChildren().setAll(scroll);

        runAsync(() -> {
            try {
                JsonObject health = api.health();
                JsonObject taskResponse = api.tasks(false);
                JsonArray list = taskResponse.getArrayOrEmpty("tasks");
                StringBuilder taskText = new StringBuilder();
                for (int i = 0; i < list.size() && i < 8; i++) {
                    JsonObject task = list.getObject(i);
                    long when = task.getLong("remindAt", task.getLong("dueAt", 0));
                    taskText.append("• ").append(task.getString("title", "Task"));
                    if (when > 0) {
                        taskText.append(" — ").append(Times.toDisplay(when,
                                java.util.TimeZone.getDefault().getID()));
                    }
                    taskText.append('\n');
                }
                if (taskText.length() == 0) {
                    taskText.append("No open tasks. Ask Indra to remind you about something.");
                }
                final String healthText = "Version " + health.getString("version", "?")
                        + " · uptime " + health.getLong("uptimeMs", 0) / 1000 + "s"
                        + " · provider " + health.getString("provider", "offline");
                final String tasksText = taskText.toString();
                Platform.runLater(() -> {
                    status.setText(healthText);
                    tasks.getChildren().setAll(new Label(tasksText));
                });
            } catch (DesktopApi.ApiException error) {
                Platform.runLater(() -> {
                    status.setText("Offline: " + error.getMessage()
                            + "\nStart the backend below, or fix the address in Settings.");
                    tasks.getChildren().setAll(new Label("Tasks are unavailable while offline."));
                });
            }
        });
    }

    // -------------------------------------------------------------------- chat

    private void showChat() {
        VBox column = new VBox(10);
        column.setPadding(new Insets(16));

        Label title = new Label("Chat with Indra");
        title.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 20px; -fx-font-weight: bold;");

        chatMessages = new VBox(8);
        chatMessages.setPadding(new Insets(8));
        chatScroll = new ScrollPane(chatMessages);
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle("-fx-background: #0C1020;");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        TextField input = field("Ask Indra anything…");
        HBox.setHgrow(input, Priority.ALWAYS);
        Button send = button("Send", true);
        Button mic = button("🎙 Talk", false);
        HBox row = new HBox(8, input, send, mic);

        Runnable submit = () -> {
            String text = input.getText().trim();
            if (text.isEmpty() || busy) {
                return;
            }
            input.clear();
            sendMessage(text);
        };
        send.setOnAction(event -> submit.run());
        input.setOnAction(event -> submit.run());
        mic.setOnAction(event -> listenOnce());

        column.getChildren().addAll(title, chatScroll, row);
        content.getChildren().setAll(column);
        addBubble("Hi, I'm Indra. Type here or tap Talk. I can research, explain, plan, and help with your "
                + "files once you confirm an action.", false);
    }

    private void sendMessage(final String text) {
        addBubble(text, true);
        busy = true;
        final StringBuilder buffer = new StringBuilder();
        final AtomicBoolean firstToken = new AtomicBoolean(true);
        runAsync(() -> {
            JsonObject meta = new JsonObject();
            try {
                api.stream(text, conversationId, java.util.TimeZone.getDefault().getID(), false,
                        config.getPersona(), config.getBrain(), config.getLanguage(),
                        new DesktopApi.StreamListener() {
                            @Override
                            public void onMeta(JsonObject value) {
                                conversationId = value.getString("conversationId", conversationId);
                            }

                            @Override
                            public void onToken(String delta) {
                                buffer.append(delta);
                                Platform.runLater(() -> {
                                    if (firstToken.getAndSet(false)) {
                                        addBubble("", false);
                                    }
                                    updateLastBubble(buffer.toString());
                                });
                            }

                            @Override
                            public void onDone(JsonObject result) {
                                final String reply = result.getString("text", buffer.toString());
                                Platform.runLater(() -> {
                                    updateLastBubble(reply);
                                    busy = false;
                                    if (config.isAutoSpeak()) {
                                        speak(reply);
                                    }
                                });
                            }

                            @Override
                            public void onError(String message) {
                                Platform.runLater(() -> {
                                    addBubble("I couldn't reach the server: " + message, false);
                                    busy = false;
                                });
                            }
                        });
            } catch (RuntimeException error) {
                Platform.runLater(() -> {
                    addBubble("Something went wrong while streaming: " + error.getMessage(), false);
                    busy = false;
                });
            }
        });
    }

    private void addBubble(String text, boolean fromUser) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(680);
        bubble.setStyle("-fx-padding: 10 14 10 14; -fx-background-radius: 14; -fx-text-fill: "
                + (fromUser ? "#FFFFFF" : "#E9EDFF") + "; -fx-background-color: "
                + (fromUser ? "#22305C" : "#111629") + ";");
        HBox row = new HBox(bubble);
        row.setAlignment(fromUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (chatMessages != null) {
            chatMessages.getChildren().add(row);
            Platform.runLater(() -> chatScroll.setVvalue(1.0));
        }
    }

    private void updateLastBubble(String text) {
        if (chatMessages == null || chatMessages.getChildren().isEmpty()) {
            addBubble(text, false);
            return;
        }
        Region last = (Region) chatMessages.getChildren().get(chatMessages.getChildren().size() - 1);
        if (last instanceof HBox && ((HBox) last).getChildren().size() == 1) {
            ((Label) ((HBox) last).getChildren().get(0)).setText(text);
            chatScroll.setVvalue(1.0);
        }
    }

    // --------------------------------------------------------------- character

    private void showCharacter() {
        VBox column = new VBox(12);
        column.setPadding(new Insets(18));
        column.setAlignment(Pos.TOP_CENTER);
        Label title = new Label("Indra");
        title.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 20px; -fx-font-weight: bold;");

        character = new CharacterCanvas(460, 460);
        character.setState(CharacterCanvas.State.IDLE);

        HBox states = new HBox(8);
        String[] labels = {"Idle", "Listening", "Thinking", "Speaking", "Happy", "Confused", "Serious"};
        for (int i = 0; i < labels.length; i++) {
            final CharacterCanvas.State state = CharacterCanvas.State.valueOf(labels[i].toUpperCase());
            Button button = button(labels[i], false);
            button.setOnAction(event -> character.setState(state));
            states.getChildren().add(button);
        }

        Button speak = button("Speak a greeting", true);
        speak.setOnAction(event -> {
            character.setState(CharacterCanvas.State.SPEAKING);
            speak("Hello, I'm Indra. It's lovely to see you. How can I help today?");
        });

        Label note = new Label("Indra is a friendly female assistant. Her expressions follow what she is doing: "
                + "rings while listening, orbit while thinking, and her mouth moves with speech.");
        note.setStyle("-fx-text-fill: #6E7799; -fx-wrap-text: true;");
        note.setMaxWidth(520);

        column.getChildren().addAll(title, character, states, speak, note);
        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #080B18;");
        content.getChildren().setAll(scroll);
    }

    // ------------------------------------------------------------------- voice

    private void showVoice() {
        VBox column = new VBox(12);
        column.setPadding(new Insets(18));
        Label title = new Label("Voice");
        title.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label engine = new Label(voice.isAvailable()
                ? "Windows speech engine detected. Indra will use a female voice when one is installed."
                : "Windows speech engine not detected. Install a speech language in Windows Settings "
                + "→ Time & Language → Speech. Voice features stay disabled until then.");
        engine.setStyle("-fx-text-fill: #9AA3C7; -fx-wrap-text: true;");

        Label transcript = new Label("");
        transcript.setStyle("-fx-text-fill: #E9EDFF; -fx-wrap-text: true;");

        Button listen = button("🎙 Listen for 8 seconds", true);
        listen.setOnAction(event -> runAsync(() -> {
            Platform.runLater(() -> transcript.setText("Listening…"));
            String heard = voice.listenOnce(8);
            Platform.runLater(() -> {
                if (heard == null) {
                    transcript.setText("I didn't catch anything. Windows dictation must be enabled "
                            + "and a microphone connected.");
                } else {
                    transcript.setText("You said: " + heard);
                    if (chatMessages == null) {
                        showChat();
                    }
                    sendMessage(heard);
                }
            });
        }));

        Button test = button("Test the voice", false);
        test.setOnAction(event -> speak("This is Indra speaking on your PC with the Windows voice engine."));

        Label hint = new Label("Voice runs entirely on your computer through Windows speech. Nothing is recorded "
                + "to disk, and no audio leaves the machine.");
        hint.setStyle("-fx-text-fill: #6E7799; -fx-wrap-text: true;");

        column.getChildren().addAll(title, card(new Label("Engine"), engine), listen, test, transcript, hint);
        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #080B18;");
        content.getChildren().setAll(scroll);
    }

    private void listenOnce() {
        if (!voice.isSupportedPlatform()) {
            info("Voice input needs Windows speech components.");
            return;
        }
        runAsync(() -> {
            String heard = voice.listenOnce(8);
            Platform.runLater(() -> {
                if (heard == null) {
                    info("I didn't catch that. Check your microphone and Windows speech settings.");
                } else {
                    sendMessage(heard);
                }
            });
        });
    }

    private void speak(String text) {
        if (!voice.isAvailable()) {
            statusUpdate("Speech output needs a Windows speech language installed");
            return;
        }
        runAsync(() -> voice.speak(text));
    }

    // ------------------------------------------------------------------ memory

    private void showMemory() {
        VBox column = new VBox(10);
        column.setPadding(new Insets(16));
        Label title = new Label("Memory");
        title.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 20px; -fx-font-weight: bold;");
        Label hint = new Label("Facts you approved. Stored on your account, synced to your phone.");
        hint.setStyle("-fx-text-fill: #9AA3C7;");

        final ListView<String> list = new ListView<String>();
        list.setStyle("-fx-background-color: #0C1020; -fx-control-inner-background: #0C1020;");
        VBox.setVgrow(list, Priority.ALWAYS);

        HBox actions = new HBox(8);
        Button refresh = button("Refresh", false);
        Button add = button("Add memory", true);
        Button delete = button("Delete selected", false);
        Button clear = button("Delete all", false);
        actions.getChildren().addAll(refresh, add, delete, clear);

        column.getChildren().addAll(title, hint, list, actions);
        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #080B18;");
        content.getChildren().setAll(scroll);

        Runnable load = () -> runAsync(() -> {
            try {
                JsonObject response = api.memories();
                JsonArray memories = response.getArrayOrEmpty("memories");
                final java.util.List<String> rows = new java.util.ArrayList<String>();
                final java.util.List<String> ids = new java.util.ArrayList<String>();
                for (int i = 0; i < memories.size(); i++) {
                    JsonObject memory = memories.getObject(i);
                    ids.add(memory.getString("id", ""));
                    rows.add(memory.getString("content", "") + "  ["
                            + memory.getString("category", "OTHER") + "]");
                }
                Platform.runLater(() -> {
                    list.setItems(FXCollections.observableArrayList(rows));
                    list.getProperties().put("ids", ids);
                });
            } catch (DesktopApi.ApiException error) {
                Platform.runLater(() -> hint.setText("Could not load memories: " + error.getMessage()));
            }
        });

        refresh.setOnAction(event -> load.run());
        add.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Add memory");
            dialog.setHeaderText("What should Indra remember about you?");
            Optional<String> value = dialog.showAndWait();
            if (value.isPresent() && !value.get().trim().isEmpty()) {
                runAsync(() -> {
                    try {
                        api.createMemory(value.get().trim(), "PROFILE", 3);
                        load.run();
                    } catch (DesktopApi.ApiException error) {
                        Platform.runLater(() -> hint.setText("Save failed: " + error.getMessage()));
                    }
                });
            }
        });
        delete.setOnAction(event -> {
            int index = list.getSelectionModel().getSelectedIndex();
            if (index < 0) {
                info("Select a memory first.");
                return;
            }
            @SuppressWarnings("unchecked")
            java.util.List<String> ids = (java.util.List<String>) list.getProperties().get("ids");
            if (ids == null || index >= ids.size()) {
                return;
            }
            final String id = ids.get(index);
            if (confirm("Delete this memory?", "This removes it from your account.")) {
                runAsync(() -> {
                    try {
                        api.deleteMemory(id);
                        load.run();
                    } catch (DesktopApi.ApiException error) {
                        Platform.runLater(() -> hint.setText("Delete failed: " + error.getMessage()));
                    }
                });
            }
        });
        clear.setOnAction(event -> {
            if (confirm("Delete all memories?", "This cannot be undone.")) {
                runAsync(() -> {
                    try {
                        api.clearMemories();
                        load.run();
                    } catch (DesktopApi.ApiException error) {
                        Platform.runLater(() -> hint.setText("Reset failed: " + error.getMessage()));
                    }
                });
            }
        });
        load.run();
    }

    // ----------------------------------------------------------------- history

    private void showHistory() {
        VBox column = new VBox(10);
        column.setPadding(new Insets(16));
        Label title = new Label("History");
        title.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 20px; -fx-font-weight: bold;");

        final ListView<String> conversations = new ListView<String>();
        conversations.setPrefHeight(220);
        conversations.setStyle("-fx-background-color: #0C1020; -fx-control-inner-background: #0C1020;");
        TextArea preview = new TextArea();
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setStyle("-fx-control-inner-background: #0C1020; -fx-text-fill: #E9EDFF;");
        VBox.setVgrow(preview, Priority.ALWAYS);

        Button reload = button("Refresh", false);
        Button open = button("Open in chat", true);
        HBox actions = new HBox(8, reload, open);

        column.getChildren().addAll(title, conversations, actions, preview);
        content.getChildren().setAll(column);

        Runnable load = () -> runAsync(() -> {
            try {
                JsonObject response = api.conversations();
                JsonArray list = response.getArrayOrEmpty("conversations");
                final java.util.List<String> rows = new java.util.ArrayList<String>();
                final java.util.List<String> ids = new java.util.ArrayList<String>();
                for (int i = 0; i < list.size(); i++) {
                    JsonObject conversation = list.getObject(i);
                    ids.add(conversation.getString("id", ""));
                    rows.add(conversation.getString("title", "Conversation") + " · "
                            + Times.toDisplay(conversation.getLong("updatedAt", 0),
                            java.util.TimeZone.getDefault().getID()));
                }
                Platform.runLater(() -> {
                    conversations.setItems(FXCollections.observableArrayList(rows));
                    conversations.getProperties().put("ids", ids);
                });
            } catch (DesktopApi.ApiException error) {
                Platform.runLater(() -> preview.setText("Could not load history: " + error.getMessage()));
            }
        });

        conversations.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            @SuppressWarnings("unchecked")
            java.util.List<String> ids = (java.util.List<String>) conversations.getProperties().get("ids");
            int index = newValue == null ? -1 : newValue.intValue();
            if (ids == null || index < 0 || index >= ids.size()) {
                return;
            }
            final String id = ids.get(index);
            runAsync(() -> {
                try {
                    JsonObject response = api.messages(id);
                    JsonArray messages = response.getArrayOrEmpty("messages");
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < messages.size(); i++) {
                        JsonObject message = messages.getObject(i);
                        text.append(message.getString("role", "")).append(": ")
                                .append(message.getString("content", "")).append("\n\n");
                    }
                    Platform.runLater(() -> preview.setText(text.toString()));
                } catch (DesktopApi.ApiException error) {
                    Platform.runLater(() -> preview.setText("Could not load messages: " + error.getMessage()));
                }
            });
        });

        open.setOnAction(event -> {
            @SuppressWarnings("unchecked")
            java.util.List<String> ids = (java.util.List<String>) conversations.getProperties().get("ids");
            int index = conversations.getSelectionModel().getSelectedIndex();
            if (ids != null && index >= 0 && index < ids.size()) {
                conversationId = ids.get(index);
                showChat();
                if (chatMessages != null) {
                    chatMessages.getChildren().clear();
                }
                addBubble("Continuing that conversation - ask me anything.", false);
            }
        });
        reload.setOnAction(event -> load.run());
        load.run();
    }

    // ------------------------------------------------------------------- files

    private void showFiles() {
        VBox column = new VBox(10);
        column.setPadding(new Insets(16));
        Label title = new Label("Files & PC control");
        title.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 20px; -fx-font-weight: bold;");
        Label hint = new Label("Everything is limited to your home folder. Deletes, moves and renames always ask first.");
        hint.setStyle("-fx-text-fill: #9AA3C7; -fx-wrap-text: true;");

        TextField query = field("Search your files (at least two characters)");
        ComboBox<String> where = new ComboBox<String>(FXCollections.observableArrayList(
                "Home", "Desktop", "Documents", "Downloads", "Pictures", "Music", "Videos"));
        where.getSelectionModel().selectFirst();
        Button search = button("Search", true);
        HBox searchRow = new HBox(8, query, where, search);
        HBox.setHgrow(query, Priority.ALWAYS);

        final ListView<String> results = new ListView<String>();
        results.setStyle("-fx-background-color: #0C1020; -fx-control-inner-background: #0C1020;");
        VBox.setVgrow(results, Priority.ALWAYS);

        HBox actions = new HBox(8);
        Button open = button("Open", false);
        Button rename = button("Rename", false);
        Button newFolder = button("New folder", false);
        Button attach = button("Attach a file to chat", false);
        actions.getChildren().addAll(open, rename, newFolder, attach);

        column.getChildren().addAll(title, hint, searchRow, results, actions);
        content.getChildren().setAll(column);

        search.setOnAction(event -> {
            String text = query.getText().trim();
            if (text.length() < 2) {
                info("Type at least two characters to search.");
                return;
            }
            JsonArray found = PcControl.searchFiles(text, where.getValue(), 60);
            java.util.List<String> rows = new java.util.ArrayList<String>();
            java.util.List<String> paths = new java.util.ArrayList<String>();
            for (int i = 0; i < found.size(); i++) {
                JsonObject entry = found.getObject(i);
                rows.add(entry.getString("name", "") + (entry.getBoolean("directory", false) ? "  [folder]" : ""));
                paths.add(entry.getString("path", ""));
            }
            results.setItems(FXCollections.observableArrayList(rows));
            results.getProperties().put("paths", paths);
            statusUpdate(found.size() + " matches in " + where.getValue());
        });

        open.setOnAction(event -> {
            @SuppressWarnings("unchecked")
            java.util.List<String> paths = (java.util.List<String>) results.getProperties().get("paths");
            int index = results.getSelectionModel().getSelectedIndex();
            if (paths == null || index < 0 || index >= paths.size()) {
                return;
            }
            statusUpdate(PcControl.openPath(paths.get(index)).message);
        });

        rename.setOnAction(event -> {
            @SuppressWarnings("unchecked")
            java.util.List<String> paths = (java.util.List<String>) results.getProperties().get("paths");
            int index = results.getSelectionModel().getSelectedIndex();
            if (paths == null || index < 0 || index >= paths.size()) {
                return;
            }
            final String path = paths.get(index);
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Rename");
            dialog.setHeaderText("New name for " + new File(path).getName());
            Optional<String> value = dialog.showAndWait();
            if (value.isPresent() && confirm("Rename this item?", path + " → " + value.get())) {
                statusUpdate(PcControl.rename(path, value.get()).message);
                search.fire();
            }
        });

        newFolder.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog("New folder");
            dialog.setTitle("New folder");
            dialog.setHeaderText("Create a folder in " + where.getValue());
            Optional<String> value = dialog.showAndWait();
            if (value.isPresent()) {
                statusUpdate(PcControl.createFolder(where.getValue(), value.get()).message);
            }
        });

        attach.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select a file to discuss");
            File selected = chooser.showOpenDialog(stage);
            if (selected == null) {
                return;
            }
            if (chatMessages == null) {
                showChat();
            }
            addBubble("Attached: " + selected.getName() + " (" + selected.length() / 1024 + " KB)", true);
            addBubble("I can see the file reference. To have me read its contents, use the Android app or "
                    + "paste the text here - I don't silently upload your files.", false);
        });
    }

    // ---------------------------------------------------------------- settings

    private void showSettings() {
        VBox column = new VBox(12);
        column.setPadding(new Insets(18));
        Label title = new Label("Settings");
        title.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 20px; -fx-font-weight: bold;");

        TextField server = field(config.getBaseUrl());
        Button saveServer = button("Save server address", false);
        saveServer.setOnAction(event -> {
            config.setBaseUrl(server.getText());
            api = new DesktopApi(config);
            refreshStatus();
            statusUpdate("Server set to " + config.getBaseUrl());
        });

        ComboBox<String> persona = new ComboBox<String>();
        for (Persona value : Persona.values()) {
            persona.getItems().add(value.getId() + " - " + value.getLabel());
        }
        persona.getSelectionModel().select(findIndex(Persona.values(), config.getPersona()));
        Button savePersona = button("Save mode", false);
        savePersona.setOnAction(event -> {
            config.setPersona(shortId(persona.getValue()));
            statusUpdate("Assistant mode saved");
        });

        ComboBox<String> brain = new ComboBox<String>();
        for (BrainProfile value : BrainProfile.values()) {
            brain.getItems().add(value.getId() + " - " + value.getLabel());
        }
        brain.getSelectionModel().select(findIndex(BrainProfile.values(), config.getBrain()));
        Button saveBrain = button("Save brain", false);
        saveBrain.setOnAction(event -> {
            config.setBrain(shortId(brain.getValue()));
            statusUpdate("Brain saved");
        });

        ComboBox<String> language = new ComboBox<String>(
                FXCollections.observableArrayList("en - English", "ne - नेपाली"));
        language.getSelectionModel().select("ne".equals(config.getLanguage()) ? 1 : 0);
        Button saveLanguage = button("Save language", false);
        saveLanguage.setOnAction(event -> {
            config.setLanguage(shortId(language.getValue()));
            statusUpdate("Language saved - Indra answers in that language when the model supports it");
        });

        Button backendToggle = button(backend.isRunning() ? "Stop backend" : "Start backend", false);
        backendToggle.setOnAction(event -> {
            String result = backend.isRunning() ? backend.stop() : backend.start();
            statusUpdate(result);
            backendToggle.setText(backend.isRunning() ? "Stop backend" : "Start backend");
            refreshStatus();
        });

        Button keys = button("Manage API keys", false);
        keys.setOnAction(event -> showKeysDialog());
        Button devices = button("Devices & pairing", false);
        devices.setOnAction(event -> showDevicesDialog());
        Button licence = button("Licence key", false);
        licence.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Licence key");
            dialog.setHeaderText("Paste the key your administrator gave you");
            Optional<String> value = dialog.showAndWait();
            if (value.isPresent() && !value.get().trim().isEmpty()) {
                runAsync(() -> {
                    try {
                        JsonObject response = api.activateLicense(value.get().trim());
                        Platform.runLater(() -> info(response.getString("message", "Licence activated")));
                    } catch (DesktopApi.ApiException error) {
                        Platform.runLater(() -> info("Licence error: " + error.getMessage()));
                    }
                });
            }
        });

        Button logout = button("Sign out", false);
        logout.setOnAction(event -> {
            if (confirm("Sign out?", "You will need to sign in again on this PC.")) {
                api.logout();
                showLogin();
            }
        });

        Label about = new Label("INDRA AI 1.1.0 · created by Umesh Chaudhary\n"
                + "Shared core module with the Android app. Your memories stay on your own server.");
        about.setStyle("-fx-text-fill: #6E7799; -fx-wrap-text: true;");

        column.getChildren().addAll(title,
                card(new Label("Backend"), new Label("Address the desktop app talks to."), server, saveServer,
                        backendToggle),
                card(new Label("Personality"), persona, savePersona, brain, saveBrain, language, saveLanguage),
                card(new Label("Account"), keys, devices, licence, logout),
                card(new Label("About"), about));

        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #080B18;");
        content.getChildren().setAll(scroll);
    }

    private void showKeysDialog() {
        runAsync(() -> {
            try {
                JsonObject response = api.keys();
                JsonArray list = response.getArrayOrEmpty("keys");
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    JsonObject key = list.getObject(i);
                    text.append("• ").append(key.getString("provider", "?")).append(" ")
                            .append(key.getString("label", "")).append(" ")
                            .append(key.getString("masked", "")).append('\n');
                }
                final String current = text.length() == 0
                        ? "No keys yet. Add the publisher key for the provider you want to use."
                        : text.toString();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("API keys");
                    alert.setHeaderText("Stored keys (masked)");
                    alert.setContentText(current + "\n\nAdd a key now?");
                    Optional<ButtonType> choice = alert.showAndWait();
                    if (choice.isPresent() && choice.get() == ButtonType.OK) {
                        TextInputDialog provider = new TextInputDialog("openai");
                        provider.setHeaderText("Provider id (openai, deepseek, openrouter, groq, github, google)");
                        Optional<String> providerId = provider.showAndWait();
                        TextInputDialog keyDialog = new TextInputDialog();
                        keyDialog.setHeaderText("Paste the key - it is stored encrypted on your server");
                        Optional<String> keyValue = keyDialog.showAndWait();
                        if (providerId.isPresent() && keyValue.isPresent()) {
                            runAsync(() -> {
                                try {
                                    api.addKey(providerId.get().trim(), "desktop", keyValue.get().trim(), true);
                                    Platform.runLater(() -> statusUpdate("Key saved for " + providerId.get()));
                                } catch (DesktopApi.ApiException error) {
                                    Platform.runLater(() -> info("Could not save the key: " + error.getMessage()));
                                }
                            });
                        }
                    }
                });
            } catch (DesktopApi.ApiException error) {
                Platform.runLater(() -> info("Could not load keys: " + error.getMessage()));
            }
        });
    }

    private void showDevicesDialog() {
        runAsync(() -> {
            try {
                JsonObject pairing = api.startPairing(config.getDeviceName(), "WINDOWS");
                api.completePairing(pairing.getString("code", ""), config.getDeviceName(), "WINDOWS");
                Platform.runLater(() -> {
                    TextInputDialog dialog = new TextInputDialog(pairing.getString("code", ""));
                    dialog.setTitle("Pair with your phone");
                    dialog.setHeaderText("Your device is registered as " + config.getDeviceName()
                            + ".\nTo pair the phone, open INDRA on Android → Settings → Devices.");
                    dialog.setContentText("Pairing code:");
                    dialog.showAndWait();
                });
            } catch (DesktopApi.ApiException error) {
                Platform.runLater(() -> info("Pairing needs a signed-in server session: "
                        + error.getMessage()));
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private void runAsync(Runnable task) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        executor.execute(task);
    }

    private void confirmed(String question, Runnable action) {
        if (confirm(question, "This action is performed only after your confirmation.")) {
            action.run();
        }
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }

    private void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("INDRA AI");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(Label label, DesktopApi.ApiException error) {
        String text;
        if (error.getStatus() == 401) {
            text = "Wrong email or password.";
        } else if (error.getStatus() == 0) {
            text = "Cannot reach the server. Start the backend and check the address.";
        } else if (error.getStatus() == 429) {
            text = "Too many attempts - wait a minute and retry.";
        } else {
            text = error.getMessage();
        }
        Platform.runLater(() -> label.setText(text));
    }

    private static int findIndex(Object[] values, String id) {
        for (int i = 0; i < values.length; i++) {
            String candidate = values[i] instanceof Persona ? ((Persona) values[i]).getId()
                    : ((BrainProfile) values[i]).getId();
            if (candidate.equalsIgnoreCase(id)) {
                return i;
            }
        }
        return 0;
    }

    private static String shortId(String selected) {
        if (selected == null) {
            return "friendly";
        }
        int dash = selected.indexOf(" - ");
        return dash > 0 ? selected.substring(0, dash) : selected;
    }

    private static TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle(fieldStyle());
        return field;
    }

    private static String fieldStyle() {
        return "-fx-background-color: #1A2138; -fx-text-fill: #E9EDFF; -fx-prompt-text-fill: #6E7799; "
                + "-fx-padding: 9; -fx-background-radius: 10;";
    }

    private static Button button(String label, boolean primary) {
        Button button = new Button(label);
        button.setStyle("-fx-background-color: " + (primary ? ACCENT : "#1A2138") + "; -fx-text-fill: "
                + (primary ? "#FFFFFF" : "#C8D1F0") + "; -fx-padding: 8 16 8 16; -fx-background-radius: 18;");
        return button;
    }

    private static Button actionButton(String label, Runnable action) {
        Button button = button(label, false);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static VBox card(javafx.scene.Node... nodes) {
        VBox card = new VBox(8, nodes);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #111629; -fx-background-radius: 14;");
        for (javafx.scene.Node node : nodes) {
            if (node instanceof Label) {
                Label label = (Label) node;
                if (label.getStyle() == null || label.getStyle().isEmpty()) {
                    label.setStyle("-fx-text-fill: #E9EDFF; -fx-font-size: 14px;");
                }
            }
        }
        return card;
    }
}
