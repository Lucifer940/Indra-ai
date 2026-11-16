package ai.indra.core.ai;

/** A chat-capable AI backend. */
public interface AiProvider {

    /** Catalogue id, e.g. {@code openai}. */
    String getId();

    /** True when this provider has everything it needs to be called. */
    boolean isConfigured();

    /** Blocking completion. */
    ChatResult complete(ChatRequest request) throws AiProviderException;

    /**
     * Streaming completion. Providers that cannot stream must throw
     * {@link AiProviderException} with {@link AiProviderException.Kind#UNSUPPORTED}.
     */
    void stream(ChatRequest request, TokenListener listener) throws AiProviderException;
}
