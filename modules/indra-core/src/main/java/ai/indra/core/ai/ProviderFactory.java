package ai.indra.core.ai;

/** Creates network-backed providers. Implemented by the backend and clients. */
public interface ProviderFactory {

    /**
     * @param providerId catalogue id, e.g. {@code openai}
     * @param apiKey     resolved key (may be empty for keyless local providers)
     * @param baseUrl    override for self-hosted gateways, or null
     */
    AiProvider create(String providerId, String apiKey, String baseUrl);
}
