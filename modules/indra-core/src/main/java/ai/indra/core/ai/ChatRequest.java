package ai.indra.core.ai;

import java.util.ArrayList;
import java.util.List;

/** A normalised chat completion request. */
public class ChatRequest {

    private final List<AiMessage> messages = new ArrayList<AiMessage>();
    private String provider = "openai";
    private String model;
    private double temperature = 0.7d;
    private int maxTokens = 900;
    private boolean stream;
    private String languageCode = "en";
    private String personaId = "friendly";
    private String brainId = "core";

    public ChatRequest add(AiMessage message) {
        if (message != null) {
            messages.add(message);
        }
        return this;
    }

    public ChatRequest system(String content) {
        messages.add(AiMessage.system(content));
        return this;
    }

    public ChatRequest user(String content) {
        messages.add(AiMessage.user(content));
        return this;
    }

    public List<AiMessage> getMessages() {
        return messages;
    }

    public String getProvider() {
        return provider;
    }

    public ChatRequest setProvider(String provider) {
        this.provider = provider;
        return this;
    }

    public String getModel() {
        return model;
    }

    public ChatRequest setModel(String model) {
        this.model = model;
        return this;
    }

    public double getTemperature() {
        return temperature;
    }

    public ChatRequest setTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public ChatRequest setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public boolean isStream() {
        return stream;
    }

    public ChatRequest setStream(boolean stream) {
        this.stream = stream;
        return this;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public ChatRequest setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
        return this;
    }

    public String getPersonaId() {
        return personaId;
    }

    public ChatRequest setPersonaId(String personaId) {
        this.personaId = personaId;
        return this;
    }

    public String getBrainId() {
        return brainId;
    }

    public ChatRequest setBrainId(String brainId) {
        this.brainId = brainId;
        return this;
    }
}
