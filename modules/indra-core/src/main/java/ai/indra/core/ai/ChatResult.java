package ai.indra.core.ai;

/** Normalised completion result. */
public class ChatResult {

    private final String text;
    private final String provider;
    private final String model;
    private final long latencyMs;
    private final int promptTokens;
    private final int completionTokens;
    private final boolean offline;

    public ChatResult(String text, String provider, String model, long latencyMs,
                      int promptTokens, int completionTokens, boolean offline) {
        this.text = text;
        this.provider = provider;
        this.model = model;
        this.latencyMs = latencyMs;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.offline = offline;
    }

    public static ChatResult error(String text) {
        return new ChatResult(text, "none", "", 0L, 0, 0, true);
    }

    public String getText() {
        return text;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public boolean isOffline() {
        return offline;
    }
}
