package ai.indra.backend.ai;

import ai.indra.core.ai.AiMessage;
import ai.indra.core.ai.AiProvider;
import ai.indra.core.ai.AiProviderException;
import ai.indra.core.ai.ChatRequest;
import ai.indra.core.ai.ChatResult;
import ai.indra.core.ai.TokenListener;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;

import java.net.http.HttpResponse;
import java.net.URLEncoder;

/** Google Gemini generateContent client. */
public final class GeminiProvider implements AiProvider {

    private final String baseUrl;
    private final String apiKey;

    public GeminiProvider(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String getId() {
        return "google";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() && baseUrl != null && !baseUrl.isEmpty();
    }

    @Override
    public ChatResult complete(ChatRequest request) throws AiProviderException {
        long started = System.currentTimeMillis();
        String model = request.getModel() == null || request.getModel().isEmpty() ? "gemini-2.0-flash" : request.getModel();
        String url = HttpProviderSupport.joinUrl(baseUrl, "models/" + URLEncoder.encode(model)) + ":generateContent";

        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        StringBuilder systemText = new StringBuilder();
        for (AiMessage message : request.getMessages()) {
            if ("system".equals(message.getRole())) {
                if (systemText.length() > 0) {
                    systemText.append("\n\n");
                }
                systemText.append(message.getContent());
                continue;
            }
            JsonObject content = new JsonObject();
            content.put("role", "assistant".equals(message.getRole()) ? "model" : "user");
            JsonArray parts = new JsonArray();
            parts.add(new JsonObject().put("text", message.getContent()));
            content.put("parts", parts);
            contents.add(content);
        }
        body.put("contents", contents);
        if (systemText.length() > 0) {
            JsonArray parts = new JsonArray();
            parts.add(new JsonObject().put("text", systemText.toString()));
            body.put("systemInstruction", new JsonObject().put("parts", parts));
        }
        JsonObject generationConfig = new JsonObject();
        generationConfig.put("temperature", Double.valueOf(request.getTemperature()));
        generationConfig.put("maxOutputTokens", Integer.valueOf(request.getMaxTokens()));
        body.put("generationConfig", generationConfig);

        HttpResponse<String> response = HttpProviderSupport.postJson(url, body.toJson(),
                "x-goog-api-key", apiKey);
        HttpProviderSupport.checkStatus(response, "Gemini");
        JsonObject json = JsonObject.parse(response.body());
        JsonArray candidates = json.getArrayOrEmpty("candidates");
        if (candidates.isEmpty()) {
            throw new AiProviderException(AiProviderException.Kind.BAD_RESPONSE, "Gemini returned no answer");
        }
        JsonObject candidate = candidates.getObject(0);
        JsonArray parts = candidate.getObjectOrEmpty("content").getArrayOrEmpty("parts");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            JsonObject part = parts.getObject(i);
            if (part != null) {
                text.append(part.getString("text", ""));
            }
        }
        JsonObject usage = json.getObjectOrEmpty("usageMetadata");
        return new ChatResult(text.toString().trim(), "google", model, System.currentTimeMillis() - started,
                usage.getInt("promptTokenCount", 0), usage.getInt("candidatesTokenCount", 0), false);
    }

    @Override
    public void stream(ChatRequest request, TokenListener listener) throws AiProviderException {
        throw new AiProviderException(AiProviderException.Kind.UNSUPPORTED,
                "Streaming is not enabled for Gemini in this build - the answer is delivered in one piece");
    }
}
