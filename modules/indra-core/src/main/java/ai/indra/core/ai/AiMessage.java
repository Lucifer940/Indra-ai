package ai.indra.core.ai;

import ai.indra.core.json.JsonObject;
import ai.indra.core.model.MessageRole;

/** Provider-agnostic chat turn. */
public class AiMessage {

    private final String role;
    private final String content;

    public AiMessage(String role, String content) {
        this.role = role == null ? "user" : role;
        this.content = content == null ? "" : content;
    }

    public static AiMessage system(String content) {
        return new AiMessage("system", content);
    }

    public static AiMessage user(String content) {
        return new AiMessage("user", content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage("assistant", content);
    }

    public static AiMessage fromRole(MessageRole role, String content) {
        if (role == MessageRole.ASSISTANT) {
            return assistant(content);
        }
        if (role == MessageRole.SYSTEM) {
            return system(content);
        }
        return user(content);
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("role", role);
        json.put("content", content);
        return json;
    }
}
