package ai.indra.backend.ai;

import ai.indra.backend.BackendConfig;
import ai.indra.core.ai.AiProvider;
import ai.indra.core.ai.ProviderCatalog;
import ai.indra.core.ai.ProviderFactory;
import ai.indra.core.ai.OfflineProvider;

/** Creates the concrete provider for a catalogue id. */
public final class HttpProviderFactory implements ProviderFactory {

    private final BackendConfig config;

    public HttpProviderFactory(BackendConfig config) {
        this.config = config;
    }

    @Override
    public AiProvider create(String providerId, String apiKey, String baseUrlOverride) {
        if (providerId == null) {
            return new OfflineProvider();
        }
        if ("offline".equals(providerId)) {
            return new OfflineProvider();
        }
        ProviderCatalog.Provider provider = ProviderCatalog.get(providerId);
        String baseUrl = baseUrlOverride == null ? provider.getBaseUrl() : baseUrlOverride;
        String configuredBase = config.providerBaseUrl(providerId);
        if (configuredBase != null) {
            baseUrl = configuredBase;
        }
        String key = apiKey == null ? "" : apiKey.trim();
        if (provider.isRequiresKey() && key.isEmpty()) {
            return null;
        }
        switch (provider.getStyle()) {
            case GEMINI:
                return new GeminiProvider(baseUrl, key);
            case ANTHROPIC:
                return new AnthropicProvider(baseUrl, key);
            case OPENAI_COMPATIBLE:
            default:
                return new OpenAiCompatibleProvider(providerId, provider.getLabel(), baseUrl, key);
        }
    }
}
