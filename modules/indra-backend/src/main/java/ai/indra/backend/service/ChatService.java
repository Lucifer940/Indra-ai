package ai.indra.backend.service;

import ai.indra.backend.http.ApiException;
import ai.indra.backend.http.SseResponder;
import ai.indra.backend.store.Repository;
import ai.indra.core.ai.AiProviderException;
import ai.indra.core.ai.ChatOrchestrator;
import ai.indra.core.ai.ChatRequest;
import ai.indra.core.ai.ChatResult;
import ai.indra.core.ai.PromptBuilder;
import ai.indra.core.ai.TokenListener;
import ai.indra.core.config.RemoteConfig;
import ai.indra.core.intent.Intent;
import ai.indra.core.intent.IntentClassifier;
import ai.indra.core.intent.IntentResult;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.memory.MemoryEngine;
import ai.indra.core.model.ChatMessage;
import ai.indra.core.model.Conversation;
import ai.indra.core.model.MessageRole;
import ai.indra.core.model.Persona;
import ai.indra.core.model.TaskItem;
import ai.indra.core.model.User;
import ai.indra.core.model.UserSettings;
import ai.indra.core.security.Validation;
import ai.indra.core.util.Times;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation brain: local intent handling for instant commands, long-term
 * memory recall, prompt construction, provider failover and persistence.
 */
public final class ChatService {

    private final Repository repository;
    private final ChatOrchestrator orchestrator;
    private final MemoryService memoryService;
    private final TaskService taskService;

    public ChatService(Repository repository, ChatOrchestrator orchestrator,
                       MemoryService memoryService, TaskService taskService) {
        this.repository = repository;
        this.orchestrator = orchestrator;
        this.memoryService = memoryService;
        this.taskService = taskService;
    }

    /** Handles one chat turn. */
    public JsonObject chat(User user, JsonObject body, String timeZone) {
        String message = Validation.requireText("message", body.getString("message"), 1, 8000);
        UserSettings settings = repository.loadSettings(user.getId());
        applyOverrides(settings, body);
        RemoteConfig remote = repository.loadConfig();
        String zone = timeZone == null ? "UTC" : timeZone;

        Conversation conversation = resolveConversation(user, body.getString("conversationId"), message);
        ChatMessage userMessage = ChatMessage.of(conversation.getId(), user.getId(), MessageRole.USER, message);
        userMessage.setLanguageCode(settings.getLanguageCode());
        userMessage.setPersonaId(settings.getPersonaId());
        userMessage.setBrainId(settings.getBrainId());
        repository.saveMessage(userMessage);

        IntentResult intent = IntentClassifier.classify(message, System.currentTimeMillis(), zone);
        String localReply = handleLocally(user, intent, message, settings, remote, zone);

        ChatResult result;
        MemoryEngine.Candidate autoCandidate = null;
        if (localReply != null) {
            result = new ChatResult(localReply, "local", "intent-engine", 0L, 0, 0, false);
        } else {
            List<ChatMessage> history = repository.listMessages(user.getId(), conversation.getId(), 20);
            history.remove(history.size() - 1); // drop the message being answered
            List<ai.indra.core.model.MemoryItem> memories = settings.isMemoryEnabled()
                    ? memoryService.recall(user, message, 12) : new ArrayList<ai.indra.core.model.MemoryItem>();
            List<TaskItem> openTasks = repository.listTasks(user.getId(), false);
            ChatRequest request = PromptBuilder.build(settings, user.getDisplayName(), history, memories,
                    openTasks, message, zone, body.getBoolean("voiceMode", false));
            try {
                result = orchestrator.send(user.getId(), request);
            } catch (AiProviderException error) {
                throw new ApiException(502, ai.indra.core.model.ApiError.PROVIDER_ERROR,
                        describeProviderError(error));
            }
            if (settings.isMemoryEnabled() && settings.isAutoMemory() && remote.isMemoryEnabled()) {
                autoCandidate = MemoryEngine.detectAutomatic(message);
            }
        }

        ChatMessage assistantMessage = ChatMessage.of(conversation.getId(), user.getId(), MessageRole.ASSISTANT,
                result.getText());
        assistantMessage.setProvider(result.getProvider());
        assistantMessage.setModel(result.getModel());
        assistantMessage.setLatencyMs(result.getLatencyMs());
        assistantMessage.setLanguageCode(settings.getLanguageCode());
        assistantMessage.setPersonaId(settings.getPersonaId());
        assistantMessage.setBrainId(settings.getBrainId());
        repository.saveMessage(assistantMessage);

        MemoryEngine.Candidate stored = null;
        if (autoCandidate != null && memoryService.remember(user, autoCandidate) != null) {
            stored = autoCandidate;
        }

        conversation.setMessageCount(conversation.getMessageCount() + 2);
        if (conversation.getTitle() == null || conversation.getTitle().trim().isEmpty()) {
            conversation.setTitle(trimTitle(message));
        }
        repository.saveConversation(conversation);

        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("reply", result.getText());
        response.put("conversationId", conversation.getId());
        response.put("messageId", assistantMessage.getId());
        response.put("provider", result.getProvider());
        response.put("model", result.getModel());
        response.put("latencyMs", Long.valueOf(result.getLatencyMs()));
        response.put("offline", Boolean.valueOf(result.isOffline()));
        response.put("intent", intent.getIntent().name());
        response.put("requiresConfirmation", Boolean.valueOf(intent.requiresConfirmation()));
        response.put("risk", intent.getRisk().name());
        response.put("personaId", settings.getPersonaId());
        response.put("brainId", settings.getBrainId());
        response.put("languageCode", settings.getLanguageCode());
        response.put("shouldSpeak", Boolean.valueOf(settings.isAutoSpeak()));
        if (stored != null) {
            response.put("memorySaved", stored.getContent());
        }
        if (intent.getIntent() == Intent.REMINDER_CREATE && intent.slotLong("at", 0L) > 0L) {
            response.put("suggestedReminderAt", Long.valueOf(intent.slotLong("at", 0L)));
            response.put("suggestedReminderTitle", intent.slot("title"));
        }
        return response;
    }

    /** Streaming variant used by the mobile and desktop clients. */
    public void stream(User user, JsonObject body, String timeZone, SseResponder sse) {
        String message = Validation.requireText("message", body.getString("message"), 1, 8000);
        UserSettings settings = repository.loadSettings(user.getId());
        applyOverrides(settings, body);
        String zone = timeZone == null ? "UTC" : timeZone;
        Conversation conversation = resolveConversation(user, body.getString("conversationId"), message);

        ChatMessage userMessage = ChatMessage.of(conversation.getId(), user.getId(), MessageRole.USER, message);
        userMessage.setLanguageCode(settings.getLanguageCode());
        repository.saveMessage(userMessage);

        IntentResult intent = IntentClassifier.classify(message, System.currentTimeMillis(), zone);
        RemoteConfig remote = repository.loadConfig();
        String localReply = handleLocally(user, intent, message, settings, remote, zone);
        if (localReply != null) {
            try {
                sse.event("meta", new JsonObject()
                        .put("conversationId", conversation.getId())
                        .put("intent", intent.getIntent().name())
                        .put("provider", "local"));
                sse.token(localReply);
                persistAssistant(user, conversation, settings, localReply, "local", "intent-engine", 0L);
                sse.done(localReply, new JsonObject()
                        .put("conversationId", conversation.getId())
                        .put("shouldSpeak", Boolean.valueOf(settings.isAutoSpeak())));
            } catch (java.io.IOException error) {
                sse.close();
            }
            return;
        }

        List<ChatMessage> history = repository.listMessages(user.getId(), conversation.getId(), 20);
        if (!history.isEmpty()) {
            history.remove(history.size() - 1);
        }
        List<ai.indra.core.model.MemoryItem> memories = settings.isMemoryEnabled()
                ? memoryService.recall(user, message, 12) : new ArrayList<ai.indra.core.model.MemoryItem>();
        ChatRequest request = PromptBuilder.build(settings, user.getDisplayName(), history, memories,
                repository.listTasks(user.getId(), false), message, zone, body.getBoolean("voiceMode", false));
        long started = System.currentTimeMillis();
        try {
            sse.event("meta", new JsonObject()
                    .put("conversationId", conversation.getId())
                    .put("intent", intent.getIntent().name())
                    .put("provider", request.getProvider())
                    .put("model", request.getModel()));
            SseTokenBridge bridge = new SseTokenBridge(sse);
            orchestrator.stream(user.getId(), request, bridge);
            persistAssistant(user, conversation, settings, bridge.text(), request.getProvider(),
                    request.getModel(), System.currentTimeMillis() - started);
        } catch (AiProviderException error) {
            try {
                sse.fail(ai.indra.core.model.ApiError.PROVIDER_ERROR, describeProviderError(error));
            } catch (java.io.IOException ignored) {
                sse.close();
            }
        }
    }

    private void persistAssistant(User user, Conversation conversation, UserSettings settings,
                                  String text, String provider, String model, long latency) {
        ChatMessage assistant = ChatMessage.of(conversation.getId(), user.getId(), MessageRole.ASSISTANT, text);
        assistant.setProvider(provider);
        assistant.setModel(model);
        assistant.setLatencyMs(latency);
        assistant.setLanguageCode(settings.getLanguageCode());
        repository.saveMessage(assistant);
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        repository.saveConversation(conversation);
    }

    /** Token listener that forwards deltas and keeps the full text for storage. */
    private final class SseTokenBridge implements TokenListener {
        private final SseResponder sse;
        private final StringBuilder buffer = new StringBuilder();

        SseTokenBridge(SseResponder sse) {
            this.sse = sse;
        }

        @Override
        public void onToken(String delta) {
            buffer.append(delta);
            try {
                sse.token(delta);
            } catch (java.io.IOException error) {
                sse.close();
            }
        }

        String text() {
            return buffer.toString();
        }

        @Override
        public void onComplete(String fullText) {
            String text = fullText == null || fullText.isEmpty() ? buffer.toString() : fullText;
            try {
                sse.done(text, new JsonObject().put("buffered", Boolean.FALSE));
            } catch (java.io.IOException error) {
                sse.close();
            }
        }

        @Override
        public void onError(AiProviderException error) {
            try {
                sse.fail(ai.indra.core.model.ApiError.PROVIDER_ERROR, describeProviderError(error));
            } catch (java.io.IOException ignored) {
                sse.close();
            }
        }
    }

    /**
     * Deterministic handling for commands that must be instant and never depend
     * on an AI provider. Returns null when the language model should answer.
     */
    private String handleLocally(User user, IntentResult intent, String message, UserSettings settings,
                                 RemoteConfig remote, String zone) {
        Intent type = intent.getIntent();
        if (type == Intent.VOICE_STOP) {
            return "Okay, I'll be quiet.";
        }
        if (type == Intent.MEMORY_REMEMBER) {
            if (!settings.isMemoryEnabled() || !remote.isMemoryEnabled()) {
                return "Memory is switched off right now, so I won't store that. You can turn it on in Settings → Memory.";
            }
            MemoryEngine.Candidate candidate = MemoryEngine.detectExplicit(message);
            if (candidate == null) {
                return "Tell me what to remember, for example: \"Remember that my exam is on 12 December\".";
            }
            memoryService.remember(user, candidate);
            return "Got it - I'll remember that: " + candidate.getContent() + ".";
        }
        if (type == Intent.MEMORY_FORGET) {
            List<ai.indra.core.model.MemoryItem> removed = memoryService.forget(user, intent.slot("topic"));
            if (removed.isEmpty()) {
                return "I couldn't find a matching memory. Open Memory in the app to see everything I know.";
            }
            return "Done - I removed " + removed.size() + " memory"
                    + (removed.size() == 1 ? "" : " entries") + " about that.";
        }
        if (type == Intent.MEMORY_LIST) {
            List<ai.indra.core.model.MemoryItem> items = repository.listMemories(user.getId(), false);
            if (items.isEmpty()) {
                return "I don't have any saved memories about you yet.";
            }
            StringBuilder reply = new StringBuilder("Here's what I remember:\n");
            int shown = 0;
            for (ai.indra.core.model.MemoryItem item : items) {
                if (shown++ >= 15) {
                    break;
                }
                reply.append("- ").append(item.getContent()).append('\n');
            }
            if (items.size() > 15) {
                reply.append("...and ").append(items.size() - 15).append(" more in the Memory screen.");
            }
            return reply.toString();
        }
        if (type == Intent.MEMORY_CLEAR) {
            int count = repository.listMemories(user.getId(), false).size();
            repository.clearMemories(user.getId(), true);
            return "All " + count + " memories are deleted. I start fresh from here.";
        }
        if (type == Intent.TASK_LIST) {
            List<TaskItem> tasks = repository.listTasks(user.getId(), false);
            if (tasks.isEmpty()) {
                return "You have no open tasks. Want me to add one?";
            }
            StringBuilder reply = new StringBuilder("Your open tasks:\n");
            int count = 0;
            for (TaskItem task : tasks) {
                if (count++ >= 12) {
                    break;
                }
                reply.append("- ").append(task.getTitle());
                long when = task.getRemindAt() > 0L ? task.getRemindAt() : task.getDueAt();
                if (when > 0L) {
                    reply.append(" (").append(Times.toDisplay(when, zone)).append(')');
                }
                reply.append('\n');
            }
            return reply.toString();
        }
        if (type == Intent.TASK_COMPLETE) {
            TaskItem task = taskService.findByTitle(user, intent.slot("title"));
            if (task == null) {
                return "I couldn't find that task. Open Tasks in the app and tell me the exact name.";
            }
            taskService.complete(user, task.getId(), zone);
            return "Marked \"" + task.getTitle() + "\" as done.";
        }
        if (type == Intent.TIME_NOW) {
            return "It's " + Times.toClock(System.currentTimeMillis(), zone) + " on "
                    + Times.toDisplay(System.currentTimeMillis(), zone) + " (" + zone + ").";
        }
        if (type == Intent.PERSONALITY_SET) {
            Persona persona = Persona.fromId(intent.slot("persona"));
            if (!remote.getAllowedPersonas().contains(persona.getId())) {
                return "That personality is switched off by the administrator right now.";
            }
            settings.setPersonaId(persona.getId());
            repository.saveSettings(settings);
            return "Done - I'm in " + persona.getLabel() + " mode now.";
        }
        if (type == Intent.BRAIN_SET) {
            ai.indra.core.model.BrainProfile brain = ai.indra.core.model.BrainProfile.fromId(intent.slot("brain"));
            if (!remote.getAllowedBrains().contains(brain.getId())) {
                return "That brain mode is switched off by the administrator right now.";
            }
            settings.setBrainId(brain.getId());
            repository.saveSettings(settings);
            return "Switched to " + brain.getLabel() + ".";
        }
        if (type == Intent.LANGUAGE_SET) {
            settings.setLanguageCode(intent.slot("language"));
            repository.saveSettings(settings);
            return "ne".equals(settings.getLanguageCode())
                    ? "ठीक छ, अब म नेपालीमा बोल्छु।"
                    : "Okay, I'll speak English from now on.";
        }
        if (type == Intent.HELP) {
            return "I can chat, remember things you tell me, set reminders and alarms, manage tasks, check weather and news, "
                    + "search the web, help with study and coding, draw on a whiteboard, and on Windows I can open apps and "
                    + "files when you confirm. Just ask naturally - or say \"Indra\" when hands-free listening is on.";
        }
        return null;
    }

    private void applyOverrides(UserSettings settings, JsonObject body) {
        if (body.has("personaId")) {
            settings.setPersonaId(Persona.fromId(body.getString("personaId")).getId());
        }
        if (body.has("brainId")) {
            settings.setBrainId(ai.indra.core.model.BrainProfile.fromId(body.getString("brainId")).getId());
        }
        if (body.has("languageCode")) {
            settings.setLanguageCode(ai.indra.core.model.Language.fromCode(body.getString("languageCode")).getCode());
        }
        if (body.has("provider") && ai.indra.core.ai.ProviderCatalog.exists(body.getString("provider"))) {
            settings.setProvider(body.getString("provider"));
        }
        if (body.has("model")) {
            settings.setModel(body.getString("model"));
        }
    }

    private Conversation resolveConversation(User user, String conversationId, String message) {
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            Conversation existing = repository.findConversation(user.getId(), conversationId);
            if (existing != null) {
                return existing;
            }
        }
        Conversation conversation = Conversation.create(user.getId(), trimTitle(message));
        conversation.setMessageCount(0);
        repository.saveConversation(conversation);
        return conversation;
    }

    private String trimTitle(String message) {
        String title = message.replaceAll("\\s+", " ").trim();
        return title.length() > 60 ? title.substring(0, 57) + "..." : title;
    }

    private String describeProviderError(AiProviderException error) {
        switch (error.getKind()) {
            case NOT_CONFIGURED:
                return "No AI provider is configured. Open Settings → AI Providers and add a key.";
            case AUTH_FAILED:
                return "The AI provider rejected the key. Check it in Settings → AI Providers.";
            case RATE_LIMITED:
                return "The AI provider is rate limiting this key. Add another key or try again in a minute.";
            case TIMEOUT:
                return "The AI provider timed out. Check your connection and try again.";
            case NETWORK:
                return "I can't reach the AI provider right now. Check your internet connection.";
            case SERVER_ERROR:
                return "The AI provider is having problems. Please try again shortly.";
            case BAD_RESPONSE:
            case UNSUPPORTED:
            default:
                return error.getMessage() == null ? "The AI provider returned an unexpected response."
                        : error.getMessage();
        }
    }

    /** Conversation list for the History screen. */
    public JsonObject conversations(User user, int limit) {
        JsonArray array = new JsonArray();
        for (Conversation conversation : repository.listConversations(user.getId(), limit)) {
            array.add(conversation.toJson());
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("conversations", array);
        return response;
    }

    public JsonObject messages(User user, String conversationId) {
        JsonArray array = new JsonArray();
        for (ChatMessage message : repository.listMessages(user.getId(), conversationId, 200)) {
            array.add(message.toJson());
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("messages", array);
        return response;
    }
}
