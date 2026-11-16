package ai.indra.core.model;

import ai.indra.core.json.JsonObject;

/** One message in a conversation. */
public class ChatMessage {

    private String id;
    private String conversationId;
    private String userId;
    private MessageRole role = MessageRole.USER;
    private String content;
    private long createdAt;
    private String languageCode = "en";
    private String personaId;
    private String brainId;
    private String provider;
    private String model;
    private long latencyMs;

    public ChatMessage() {
    }

    public static ChatMessage of(String conversationId, String userId, MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.id = ai.indra.core.util.Ids.uuid();
        message.conversationId = conversationId;
        message.userId = userId;
        message.role = role;
        message.content = content;
        message.createdAt = System.currentTimeMillis();
        return message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role == null ? MessageRole.USER : role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getPersonaId() {
        return personaId;
    }

    public void setPersonaId(String personaId) {
        this.personaId = personaId;
    }

    public String getBrainId() {
        return brainId;
    }

    public void setBrainId(String brainId) {
        this.brainId = brainId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("id", id);
        json.put("conversationId", conversationId);
        json.put("userId", userId);
        json.put("role", role.name());
        json.put("content", content);
        json.put("createdAt", Long.valueOf(createdAt));
        json.put("languageCode", languageCode);
        json.put("personaId", personaId);
        json.put("brainId", brainId);
        json.put("provider", provider);
        json.put("model", model);
        json.put("latencyMs", Long.valueOf(latencyMs));
        return json;
    }

    public static ChatMessage fromJson(JsonObject json) {
        ChatMessage message = new ChatMessage();
        message.id = json.getString("id");
        message.conversationId = json.getString("conversationId");
        message.userId = json.getString("userId");
        message.role = MessageRole.fromName(json.getString("role", "USER"));
        message.content = json.getString("content");
        message.createdAt = json.getLong("createdAt", 0L);
        message.languageCode = json.getString("languageCode", "en");
        message.personaId = json.getString("personaId");
        message.brainId = json.getString("brainId");
        message.provider = json.getString("provider");
        message.model = json.getString("model");
        message.latencyMs = json.getLong("latencyMs", 0L);
        return message;
    }
}
