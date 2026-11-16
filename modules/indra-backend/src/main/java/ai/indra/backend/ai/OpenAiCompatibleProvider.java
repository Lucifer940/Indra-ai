package ai.indra.backend.ai;

import ai.indra.core.ai.AiMessage;
import ai.indra.core.ai.AiProvider;
import ai.indra.core.ai.AiProviderException;
import ai.indra.core.ai.ChatRequest;
import ai.indra.core.ai.ChatResult;
import ai.indra.core.ai.TokenListener;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * OpenAI Chat Completions compatible client. Works for OpenAI, DeepSeek,
 * OpenRouter, Groq, GitHub Models, Ollama and LM Studio - they all share this
 * wire format.
 */
public final class OpenAiCompatibleProvider implements AiProvider {

    private final String providerId;
    private final String label;
    private final String baseUrl;
    private final String apiKey;

    public OpenAiCompatibleProvider(String providerId, String label, String baseUrl, String apiKey) {
        this.providerId = providerId;
        this.label = label;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String getId() {
        return providerId;
    }

    @Override
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isEmpty();
    }

    private String endpoint() {
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return HttpProviderSupport.joinUrl(baseUrl, "chat/completions");
    }

    private JsonObject payload(ChatRequest request, boolean stream) {
        JsonObject body = new JsonObject();
        body.put("model", request.getModel());
        JsonArray messages = new JsonArray();
        for (AiMessage message : request.getMessages()) {
            messages.add(message.toJson());
        }
        body.put("messages", messages);
        body.put("temperature", Double.valueOf(request.getTemperature()));
        body.put("max_tokens", Integer.valueOf(request.getMaxTokens()));
        body.put("stream", Boolean.valueOf(stream));
        return body;
    }

    @Override
    public ChatResult complete(ChatRequest request) throws AiProviderException {
        long started = System.currentTimeMillis();
        HttpResponse<String> response = HttpProviderSupport.postJson(endpoint(), payload(request, false).toJson(),
                "Authorization", "Bearer " + apiKey);
        HttpProviderSupport.checkStatus(response, label);
        JsonObject json = JsonObject.parse(response.body());
        JsonArray choices = json.getArrayOrEmpty("choices");
        if (choices.isEmpty()) {
            throw new AiProviderException(AiProviderException.Kind.BAD_RESPONSE, label + " returned no answer");
        }
        JsonObject message = choices.getObject(0).getObjectOrEmpty("message");
        String text = message.getString("content", "");
        if (text.trim().isEmpty()) {
            String reasoning = message.getString("reasoning_content", "");
            text = reasoning;
        }
        JsonObject usage = json.getObjectOrEmpty("usage");
        return new ChatResult(text.trim(), providerId, request.getModel(),
                System.currentTimeMillis() - started,
                usage.getInt("prompt_tokens", 0), usage.getInt("completion_tokens", 0), false);
    }

    @Override
    public void stream(ChatRequest request, TokenListener listener) throws AiProviderException {
        HttpRequest httpRequest = HttpProviderSupport.requestBuilder(endpoint(),
                "Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload(request, true).toJson(), StandardCharsets.UTF_8))
                .build();
        StringBuilder full = new StringBuilder();
        try {
            HttpResponse<java.io.InputStream> response = HttpProviderSupport.CLIENT.send(httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                response.body().close();
                if (status == 401 || status == 403) {
                    throw new AiProviderException(AiProviderException.Kind.AUTH_FAILED,
                            label + " rejected the API key.", status, null);
                }
                if (status == 429) {
                    throw new AiProviderException(AiProviderException.Kind.RATE_LIMITED,
                            label + " rate limit reached.", status, null);
                }
                throw new AiProviderException(AiProviderException.Kind.SERVER_ERROR,
                        label + " streaming failed with status " + status, status, null);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonObject chunk = JsonObject.parse(data);
                    JsonArray choices = chunk.getArrayOrEmpty("choices");
                    if (choices.isEmpty()) {
                        continue;
                    }
                    JsonObject delta = choices.getObject(0).getObjectOrEmpty("delta");
                    String text = delta.getString("content", "");
                    if (!text.isEmpty()) {
                        full.append(text);
                        listener.onToken(text);
                    }
                }
            }
            listener.onComplete(full.toString());
        } catch (AiProviderException error) {
            listener.onError(error);
            throw error;
        } catch (java.io.IOException error) {
            AiProviderException wrapped = new AiProviderException(AiProviderException.Kind.NETWORK,
                    "Streaming failed: " + error.getMessage(), 0, error);
            listener.onError(wrapped);
            throw wrapped;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            AiProviderException wrapped = new AiProviderException(AiProviderException.Kind.NETWORK,
                    "Streaming was interrupted", 0, error);
            listener.onError(wrapped);
            throw wrapped;
        }
    }

}
