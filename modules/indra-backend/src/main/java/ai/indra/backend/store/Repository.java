package ai.indra.backend.store;

import ai.indra.core.config.RemoteConfig;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.ApiKeyEntry;
import ai.indra.core.model.ChatMessage;
import ai.indra.core.model.Conversation;
import ai.indra.core.model.Device;
import ai.indra.core.model.LicenseRecord;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.model.TaskItem;
import ai.indra.core.model.User;
import ai.indra.core.model.UserSettings;
import ai.indra.core.util.Ids;
import ai.indra.core.util.Times;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Typed data access on top of {@link DocumentStore}. */
public final class Repository {

    public static final String USERS = "users";
    public static final String MEMORIES = "memories";
    public static final String CONVERSATIONS = "conversations";
    public static final String MESSAGES = "messages";
    public static final String TASKS = "tasks";
    public static final String SETTINGS = "settings";
    public static final String DEVICES = "devices";
    public static final String PAIRINGS = "pairings";
    public static final String LICENSES = "licenses";
    public static final String API_KEYS = "apikeys";
    public static final String API_SECRETS = "apikey_secrets";
    public static final String AUDIT = "audit";
    public static final String CONFIG = "config";
    public static final String META = "meta";

    private final DocumentStore store;

    public Repository(DocumentStore store) {
        this.store = store;
    }

    public DocumentStore getStore() {
        return store;
    }

    // ------------------------------------------------------------------ users

    public User findUserByEmail(String email) {
        if (email == null) {
            return null;
        }
        JsonObject doc = store.findByLookup(USERS, "email:" + email.trim().toLowerCase(Locale.ROOT));
        return doc == null ? null : User.fromJson(doc);
    }

    public User findUser(String id) {
        if (id == null) {
            return null;
        }
        JsonObject doc = store.get(USERS, id);
        return doc == null ? null : User.fromJson(doc);
    }

    public void saveUser(User user) {
        user.setUpdatedAt(System.currentTimeMillis());
        store.put(USERS, user.getId(), user.getId(),
                "email:" + (user.getEmail() == null ? "" : user.getEmail().toLowerCase(Locale.ROOT)),
                user.getUpdatedAt(), withPasswordHash(user));
    }

    /** The stored document keeps the password hash; the API-facing JSON does not. */
    private JsonObject withPasswordHash(User user) {
        JsonObject json = user.toJson();
        json.put("passwordHash", user.getPasswordHash());
        json.put("providerSubject", user.getProviderSubject());
        return json;
    }

    public List<User> listUsers(int limit, int offset) {
        List<JsonObject> docs = store.list(USERS, null, 0L, 0);
        List<User> users = new ArrayList<User>();
        int skipped = 0;
        for (JsonObject doc : docs) {
            if (skipped++ < offset) {
                continue;
            }
            if (limit > 0 && users.size() >= limit) {
                break;
            }
            users.add(User.fromJson(doc));
        }
        return users;
    }

    public int countUsers() {
        return store.count(USERS);
    }

    // --------------------------------------------------------------- memories

    public List<MemoryItem> listMemories(String userId, boolean includeDeleted) {
        List<MemoryItem> items = new ArrayList<MemoryItem>();
        for (JsonObject doc : store.list(MEMORIES, userId, 0L, 0)) {
            MemoryItem item = MemoryItem.fromJson(doc);
            if (!includeDeleted && item.isDeleted()) {
                continue;
            }
            items.add(item);
        }
        items.sort(new java.util.Comparator<MemoryItem>() {
            @Override
            public int compare(MemoryItem left, MemoryItem right) {
                return Long.compare(right.getUpdatedAt(), left.getUpdatedAt());
            }
        });
        return items;
    }

    public MemoryItem findMemory(String userId, String id) {
        JsonObject doc = store.get(MEMORIES, id);
        if (doc == null) {
            return null;
        }
        MemoryItem item = MemoryItem.fromJson(doc);
        return userId.equals(item.getUserId()) ? item : null;
    }

    public void saveMemory(MemoryItem item) {
        item.setUpdatedAt(System.currentTimeMillis());
        store.put(MEMORIES, item.getId(), item.getUserId(), null, item.getUpdatedAt(), item.toJson());
    }

    public void deleteMemory(String userId, String id, boolean hard) {
        MemoryItem item = findMemory(userId, id);
        if (item == null) {
            return;
        }
        if (hard) {
            store.delete(MEMORIES, id);
            return;
        }
        item.setDeletedAt(System.currentTimeMillis());
        item.setUpdatedAt(item.getDeletedAt());
        store.put(MEMORIES, item.getId(), userId, null, item.getUpdatedAt(), item.toJson());
    }

    public void clearMemories(String userId, boolean hard) {
        for (MemoryItem item : listMemories(userId, true)) {
            deleteMemory(userId, item.getId(), hard);
        }
    }

    // ---------------------------------------------------------- conversations

    public Conversation findConversation(String userId, String id) {
        JsonObject doc = store.get(CONVERSATIONS, id);
        if (doc == null) {
            return null;
        }
        Conversation conversation = Conversation.fromJson(doc);
        return userId.equals(conversation.getUserId()) ? conversation : null;
    }

    public void saveConversation(Conversation conversation) {
        conversation.setUpdatedAt(System.currentTimeMillis());
        store.put(CONVERSATIONS, conversation.getId(), conversation.getUserId(), null,
                conversation.getUpdatedAt(), conversation.toJson());
    }

    public List<Conversation> listConversations(String userId, int limit) {
        List<Conversation> conversations = new ArrayList<Conversation>();
        for (JsonObject doc : store.list(CONVERSATIONS, userId, 0L, 0)) {
            conversations.add(Conversation.fromJson(doc));
        }
        conversations.sort(new java.util.Comparator<Conversation>() {
            @Override
            public int compare(Conversation left, Conversation right) {
                return Long.compare(right.getUpdatedAt(), left.getUpdatedAt());
            }
        });
        if (limit > 0 && conversations.size() > limit) {
            return new ArrayList<Conversation>(conversations.subList(0, limit));
        }
        return conversations;
    }

    public void deleteConversation(String userId, String id) {
        Conversation conversation = findConversation(userId, id);
        if (conversation == null) {
            return;
        }
        for (ChatMessage message : listMessages(userId, id, 0)) {
            store.delete(MESSAGES, message.getId());
        }
        store.delete(CONVERSATIONS, id);
    }

    public void saveMessage(ChatMessage message) {
        store.put(MESSAGES, message.getId(), message.getUserId(), null, message.getCreatedAt(), message.toJson());
    }

    public List<ChatMessage> listMessages(String userId, String conversationId, int limit) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        for (JsonObject doc : store.list(MESSAGES, userId, 0L, 0)) {
            ChatMessage message = ChatMessage.fromJson(doc);
            if (conversationId != null && !conversationId.equals(message.getConversationId())) {
                continue;
            }
            messages.add(message);
        }
        messages.sort(new java.util.Comparator<ChatMessage>() {
            @Override
            public int compare(ChatMessage left, ChatMessage right) {
                return Long.compare(left.getCreatedAt(), right.getCreatedAt());
            }
        });
        if (limit > 0 && messages.size() > limit) {
            return new ArrayList<ChatMessage>(messages.subList(messages.size() - limit, messages.size()));
        }
        return messages;
    }

    // ------------------------------------------------------------------ tasks

    public List<TaskItem> listTasks(String userId, boolean includeCompleted) {
        List<TaskItem> tasks = new ArrayList<TaskItem>();
        for (JsonObject doc : store.list(TASKS, userId, 0L, 0)) {
            TaskItem task = TaskItem.fromJson(doc);
            if (!includeCompleted && task.isCompleted()) {
                continue;
            }
            tasks.add(task);
        }
        tasks.sort(new java.util.Comparator<TaskItem>() {
            @Override
            public int compare(TaskItem left, TaskItem right) {
                long leftWhen = left.getRemindAt() > 0L ? left.getRemindAt() : left.getDueAt();
                long rightWhen = right.getRemindAt() > 0L ? right.getRemindAt() : right.getDueAt();
                return Long.compare(leftWhen, rightWhen);
            }
        });
        return tasks;
    }

    public TaskItem findTask(String userId, String id) {
        JsonObject doc = store.get(TASKS, id);
        if (doc == null) {
            return null;
        }
        TaskItem task = TaskItem.fromJson(doc);
        return userId.equals(task.getUserId()) ? task : null;
    }

    public void saveTask(TaskItem task) {
        task.setUpdatedAt(System.currentTimeMillis());
        store.put(TASKS, task.getId(), task.getUserId(), null, task.getUpdatedAt(), task.toJson());
    }

    public void deleteTask(String userId, String id) {
        TaskItem task = findTask(userId, id);
        if (task != null) {
            store.delete(TASKS, id);
        }
    }

    // --------------------------------------------------------------- settings

    public UserSettings loadSettings(String userId) {
        JsonObject doc = store.get(SETTINGS, userId);
        if (doc == null) {
            return UserSettings.defaults(userId);
        }
        return UserSettings.fromJson(doc);
    }

    public void saveSettings(UserSettings settings) {
        settings.setUpdatedAt(System.currentTimeMillis());
        store.put(SETTINGS, settings.getUserId(), settings.getUserId(), null, settings.getUpdatedAt(), settings.toJson());
    }

    // ---------------------------------------------------------------- devices

    public List<Device> listDevices(String userId) {
        List<Device> devices = new ArrayList<Device>();
        for (JsonObject doc : store.list(DEVICES, userId, 0L, 0)) {
            devices.add(Device.fromJson(doc));
        }
        return devices;
    }

    public Device findDevice(String userId, String id) {
        JsonObject doc = store.get(DEVICES, id);
        if (doc == null) {
            return null;
        }
        Device device = Device.fromJson(doc);
        return userId.equals(device.getUserId()) ? device : null;
    }

    public void saveDevice(Device device) {
        store.put(DEVICES, device.getId(), device.getUserId(), null,
                Math.max(device.getLastSeenAt(), device.getPairedAt()), device.toJson());
    }

    public void deleteDevice(String userId, String id) {
        Device device = findDevice(userId, id);
        if (device != null) {
            store.delete(DEVICES, id);
        }
    }

    // --------------------------------------------------------------- pairings

    public void savePairing(JsonObject pairing) {
        String id = pairing.getString("id");
        store.put(PAIRINGS, id, pairing.getString("userId"), pairing.getString("lookupKey"),
                pairing.getLong("updatedAt", System.currentTimeMillis()), pairing);
    }

    public JsonObject findPairing(String lookupKey) {
        return store.findByLookup(PAIRINGS, lookupKey);
    }

    public JsonObject getPairing(String id) {
        return store.get(PAIRINGS, id);
    }

    public void deletePairing(String id) {
        store.delete(PAIRINGS, id);
    }

    public List<JsonObject> listPairings(String userId) {
        return store.list(PAIRINGS, userId, 0L, 0);
    }

    // --------------------------------------------------------------- licences

    public void saveLicense(LicenseRecord record) {
        store.put(LICENSES, record.getKey(), record.getBoundUserId(), "license:" + record.getKey(),
                Math.max(record.getIssuedAt(), 1L), record.toJson());
    }

    public LicenseRecord findLicense(String key) {
        JsonObject doc = store.get(LICENSES, key);
        return doc == null ? null : LicenseRecord.fromJson(doc);
    }

    public List<LicenseRecord> listLicenses() {
        List<LicenseRecord> records = new ArrayList<LicenseRecord>();
        for (JsonObject doc : store.list(LICENSES, null, 0L, 0)) {
            records.add(LicenseRecord.fromJson(doc));
        }
        return records;
    }

    // --------------------------------------------------------------- api keys

    public List<ApiKeyEntry> loadApiKeys(String userId) {
        List<ApiKeyEntry> entries = new ArrayList<ApiKeyEntry>();
        for (JsonObject doc : store.list(API_KEYS, userId, 0L, 0)) {
            entries.add(ApiKeyEntry.fromJson(doc));
        }
        return entries;
    }

    public void saveApiKey(String userId, ApiKeyEntry entry) {
        entry.setOwnerId(userId);
        store.put(API_KEYS, entry.getId(), userId, null,
                Math.max(entry.getLastUsedAt(), entry.getCreatedAt()), entry.toJson());
    }

    public void deleteApiKey(String userId, String entryId) {
        JsonObject doc = store.get(API_KEYS, entryId);
        if (doc != null && userId.equals(ApiKeyEntry.fromJson(doc).getOwnerId())) {
            store.delete(API_KEYS, entryId);
        }
    }

    public void saveApiKeySecret(String userId, String entryId, String encrypted) {
        JsonObject doc = new JsonObject();
        doc.put("id", entryId);
        doc.put("secret", encrypted);
        doc.put("updatedAt", Long.valueOf(System.currentTimeMillis()));
        store.put(API_SECRETS, entryId, userId, null, System.currentTimeMillis(), doc);
    }

    public String loadApiKeySecret(String entryId) {
        JsonObject doc = store.get(API_SECRETS, entryId);
        return doc == null ? null : doc.getString("secret");
    }

    public void deleteApiKeySecret(String entryId) {
        store.delete(API_SECRETS, entryId);
    }

    public void clearApiKeys(String userId) {
        for (ApiKeyEntry entry : loadApiKeys(userId)) {
            deleteApiKeySecret(entry.getId());
            store.delete(API_KEYS, entry.getId());
        }
    }

    // ------------------------------------------------------------------ audit

    public void audit(String actorId, String action, String detail, String ip) {
        JsonObject doc = new JsonObject();
        String id = Ids.uuid();
        doc.put("id", id);
        doc.put("actorId", actorId);
        doc.put("action", action);
        doc.put("detail", ai.indra.core.util.Strings.truncate(detail, 500));
        doc.put("ip", ip);
        doc.put("createdAt", Long.valueOf(System.currentTimeMillis()));
        doc.put("createdAtIso", Times.toIso(System.currentTimeMillis()));
        store.put(AUDIT, id, actorId, null, System.currentTimeMillis(), doc);
    }

    public List<JsonObject> recentAudit(int limit) {
        List<JsonObject> entries = new ArrayList<JsonObject>(store.list(AUDIT, null, 0L, 0));
        entries.sort(new java.util.Comparator<JsonObject>() {
            @Override
            public int compare(JsonObject left, JsonObject right) {
                return Long.compare(right.getLong("createdAt", 0L), left.getLong("createdAt", 0L));
            }
        });
        if (limit > 0 && entries.size() > limit) {
            return new ArrayList<JsonObject>(entries.subList(0, limit));
        }
        return entries;
    }

    // ----------------------------------------------------------------- config

    public RemoteConfig loadConfig() {
        JsonObject doc = store.get(CONFIG, "app");
        if (doc == null) {
            RemoteConfig defaults = RemoteConfig.defaults().setRevision(1L);
            saveConfig(defaults);
            return defaults;
        }
        return RemoteConfig.fromJson(doc);
    }

    public void saveConfig(RemoteConfig config) {
        config.setUpdatedAt(System.currentTimeMillis());
        store.put(CONFIG, "app", null, null, config.getUpdatedAt(), config.toJson());
    }

    // ------------------------------------------------------------------- meta

    public String getMeta(String key, String fallback) {
        JsonObject doc = store.get(META, key);
        return doc == null ? fallback : doc.getString("value", fallback);
    }

    public void setMeta(String key, String value) {
        JsonObject doc = new JsonObject();
        doc.put("key", key);
        doc.put("value", value);
        doc.put("updatedAt", Long.valueOf(System.currentTimeMillis()));
        store.put(META, key, null, null, System.currentTimeMillis(), doc);
    }
}
