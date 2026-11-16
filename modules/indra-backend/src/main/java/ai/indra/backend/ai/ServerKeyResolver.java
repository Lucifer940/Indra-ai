package ai.indra.backend.ai;

import ai.indra.backend.BackendConfig;
import ai.indra.core.ai.KeyResolver;
import ai.indra.core.ai.ProviderCatalog;
import ai.indra.core.apikeys.ApiKeyVault;

import java.util.ArrayList;
import java.util.List;

/**
 * Key resolution order: the user's own vault keys first (so a user's personal
 * key is preferred and their quota is used), then the server's environment keys
 * so the app works out of the box when the operator configured one.
 */
public final class ServerKeyResolver implements KeyResolver {

    private final ApiKeyVault vault;
    private final BackendConfig config;

    public ServerKeyResolver(ApiKeyVault vault, BackendConfig config) {
        this.vault = vault;
        this.config = config;
    }

    @Override
    public List<String> keysFor(String ownerId, String providerId) {
        List<String> keys = new ArrayList<String>(vault.usableKeys(ownerId, providerId));
        String serverKey = config.providerKey(providerId);
        if (serverKey != null && !serverKey.isEmpty() && !keys.contains(serverKey)) {
            keys.add(serverKey);
        }
        return keys;
    }

    @Override
    public boolean hasKeys(String ownerId, String providerId) {
        return !keysFor(ownerId, providerId).isEmpty();
    }

    @Override
    public void reportFailure(String ownerId, String providerId, String key) {
        String serverKey = config.providerKey(providerId);
        if (serverKey == null || !serverKey.equals(key)) {
            vault.markFailure(ownerId, providerId, key);
        }
    }

    @Override
    public void reportSuccess(String ownerId, String providerId, String key) {
        String serverKey = config.providerKey(providerId);
        if (serverKey == null || !serverKey.equals(key)) {
            vault.markSuccess(ownerId, providerId, key);
        }
    }

    @Override
    public boolean isKeyless(String providerId) {
        return !ProviderCatalog.get(providerId).isRequiresKey();
    }
}
