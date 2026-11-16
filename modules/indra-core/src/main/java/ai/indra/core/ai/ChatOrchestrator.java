package ai.indra.core.ai;

import ai.indra.core.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a chat request with automatic key rotation and provider failover:
 * requested provider first, then any other provider that has a usable key, and
 * finally the deterministic offline brain. Streaming requests fall back to a
 * blocking call when the chosen provider cannot stream.
 */
public final class ChatOrchestrator {

    private static final int MAX_KEYS_PER_PROVIDER = 3;

    private final ProviderFactory factory;
    private final KeyResolver resolver;

    public ChatOrchestrator(ProviderFactory factory, KeyResolver resolver) {
        this.factory = factory;
        this.resolver = resolver;
    }

    /** Provider order for a request: preferred, other keyed providers, offline. */
    public List<String> planFor(String ownerId, ChatRequest request) {
        List<String> plan = new ArrayList<String>();
        String requested = request == null ? null : request.getProvider();
        if (requested != null && ProviderCatalog.exists(requested) && !"offline".equals(requested)) {
            plan.add(requested);
        }
        for (String id : ProviderCatalog.ids()) {
            if ("offline".equals(id) || id.equals(requested)) {
                continue;
            }
            ProviderCatalog.Provider provider = ProviderCatalog.get(id);
            if (!provider.isRequiresKey()) {
                plan.add(id); // local server such as Ollama might be running
            } else if (resolver.hasKeys(ownerId, id)) {
                plan.add(id);
            }
        }
        plan.add("offline");
        return plan;
    }

    public ChatResult send(String ownerId, ChatRequest request) throws AiProviderException {
        List<String> plan = planFor(ownerId, request);
        AiProviderException lastError = null;
        for (String providerId : plan) {
            ProviderCatalog.Provider catalog = ProviderCatalog.get(providerId);
            List<String> keys = new ArrayList<String>();
            if (catalog.isRequiresKey()) {
                keys.addAll(resolver.keysFor(ownerId, providerId));
                if (keys.isEmpty()) {
                    continue;
                }
            } else {
                keys.add("");
            }
            int attempts = 0;
            for (String key : keys) {
                if (attempts++ >= MAX_KEYS_PER_PROVIDER) {
                    break;
                }
                AiProvider provider = factory.create(providerId, key, null);
                if (provider == null) {
                    continue;
                }
                try {
                    ChatResult result = provider.complete(decorate(request, providerId));
                    if (catalog.isRequiresKey() && !key.isEmpty()) {
                        resolver.reportSuccess(ownerId, providerId, key);
                    }
                    return result;
                } catch (AiProviderException error) {
                    lastError = error;
                    Log.warn("ChatOrchestrator", "Provider " + providerId + " failed: " + error.getKind() + " " + error.getMessage());
                    if (catalog.isRequiresKey() && !key.isEmpty() && error.isRetryableElsewhere()) {
                        resolver.reportFailure(ownerId, providerId, key);
                    }
                    if (error.getKind() == AiProviderException.Kind.UNSUPPORTED) {
                        break;
                    }
                }
            }
        }
        if (lastError != null) {
            throw new AiProviderException(AiProviderException.Kind.SERVER_ERROR,
                    "All AI providers failed. Last error: " + lastError.getMessage(), lastError.getStatusCode(), lastError);
        }
        throw new AiProviderException(AiProviderException.Kind.NOT_CONFIGURED,
                "No AI provider is configured yet. Add a key in Settings.");
    }

    public void stream(String ownerId, ChatRequest request, TokenListener listener) throws AiProviderException {
        ChatRequest streamRequest = decorate(request, request.getProvider());
        streamRequest.setStream(true);
        List<String> plan = planFor(ownerId, request);
        AiProviderException lastError = null;
        for (String providerId : plan) {
            ProviderCatalog.Provider catalog = ProviderCatalog.get(providerId);
            List<String> keys = new ArrayList<String>();
            if (catalog.isRequiresKey()) {
                keys.addAll(resolver.keysFor(ownerId, providerId));
                if (keys.isEmpty()) {
                    continue;
                }
            } else {
                keys.add("");
            }
            if (!catalog.isSupportsStreaming()) {
                keys.clear();
            }
            for (String key : keys) {
                AiProvider provider = factory.create(providerId, key, null);
                if (provider == null) {
                    continue;
                }
                try {
                    provider.stream(streamRequest, listener);
                    if (catalog.isRequiresKey() && !key.isEmpty()) {
                        resolver.reportSuccess(ownerId, providerId, key);
                    }
                    return;
                } catch (AiProviderException error) {
                    lastError = error;
                    if (catalog.isRequiresKey() && !key.isEmpty() && error.isRetryableElsewhere()) {
                        resolver.reportFailure(ownerId, providerId, key);
                    }
                }
            }
        }
        // No provider could stream: fall back to a blocking answer delivered as one chunk.
        ChatResult result = send(ownerId, request);
        listener.onToken(result.getText());
        listener.onComplete(result.getText());
        if (lastError != null) {
            Log.warn("ChatOrchestrator", "Streaming unavailable, used blocking fallback");
        }
    }

    private ChatRequest decorate(ChatRequest request, String providerId) {
        ChatRequest copy = request;
        copy.setProvider(providerId);
        if (copy.getModel() == null || copy.getModel().trim().isEmpty()) {
            copy.setModel(ProviderCatalog.get(providerId).getDefaultModel());
        }
        return copy;
    }
}
