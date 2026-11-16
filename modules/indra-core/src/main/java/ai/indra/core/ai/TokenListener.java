package ai.indra.core.ai;

/** Receives incremental tokens for streaming responses. */
public interface TokenListener {

    /** One text delta; may be called many times on a background thread. */
    void onToken(String delta);

    /** Called once with the full text. */
    void onComplete(String fullText);

    /** Called instead of onComplete when streaming fails. */
    void onError(AiProviderException error);
}
