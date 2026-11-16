package ai.indra.backend.ai;

import ai.indra.core.ai.AiMessage;
import ai.indra.core.ai.AiProvider;
import ai.indra.core.ai.AiProviderException;
import ai.indra.core.ai.ChatRequest;
import ai.indra.core.ai.ChatResult;
import ai.indra.core.ai.TokenListener;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;


/** Anthropic Messages API client. */
public final class AnthropicProvider implements AiProvider {

    private final String baseUrl;
    private final String apiKey;

    public AnthropicProvider(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String getId() {
        return "anthropic";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() && baseUrl != null && !baseUrl.isEmpty();
    }

    @Override
    public ChatResult complete(ChatRequest request) throws AiProviderException {
        long started = System.currentTimeMillis();
        String model = request.getModel() == null || request.getModel().isEmpty()
                ? "claude-3-5-sonnet-latest" : request.getModel();
        JsonObject body = new JsonObject();
        body.put("model", model);
        body.put("max_tokens", Integer.valueOf(request.getMaxTokens()));
        body.put("temperature", Double.valueOf(request.getTemperature()));

        StringBuilder system = new StringBuilder();
        JsonArray messages = new JsonArray();
        for (AiMessage message : request.getMessages()) {
            if ("system".equals(message.getRole())) {
                if (system.length() > 0) {
                    system.append("\n\n");
                }
                system.append(message.getContent());
                continue;
            }
            JsonObject turn = new JsonObject();
            turn.put("role", "assistant".equals(message.getRole()) ? "assistant" : "user");
            turn.put("content", message.getContent());
            messages.add(turn);
        }
        if (system.length() > 0) {
            body.put("system", system.toString());
        }
        body.put("messages", messages);

        HttpProviderSupport.JsonResponse response = HttpProviderSupport.postJson(
                HttpProviderSupport.joinUrl(baseUrl, "messages"), body.toJson(),
                "x-api-key", apiKey, "anthropic-version", "2023-06-01");
        HttpProviderSupport.checkStatus(response, "Claude");
        JsonObject json = JsonObject.parse(response.body());
        JsonArray content = json.getArrayOrEmpty("content");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            JsonObject block = content.getObject(i);
            if (block != null && "text".equals(block.getString("type", "text"))) {
                text.append(block.getString("text", ""));
            }
        }
        JsonObject usage = json.getObjectOrEmpty("usage");
        return new ChatResult(text.toString().trim(), "anthropic", model, System.currentTimeMillis() - started,
                usage.getInt("input_tokens", 0), usage.getInt("output_tokens", 0), false);
    }

    @Override
    public void stream(ChatRequest request, TokenListener listener) throws AiProviderException {
        throw new AiProviderException(AiProviderException.Kind.UNSUPPORTED,
                "Streaming is not enabled for Claude in this build - the answer is delivered in one piece");
    }
}
