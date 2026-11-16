package ai.indra.core.ai;

import ai.indra.core.apikeys.ApiKeyVault;
import ai.indra.core.ai.ProviderCatalog;

import java.util.List;

/** Bridges {@link ApiKeyVault} to the orchestrator. */
public final class VaultKeyResolver implements KeyResolver {

    private final ApiKeyVault vault;

    public VaultKeyResolver(ApiKeyVault vault) {
        this.vault = vault;
    }

    @Override
    public List<String> keysFor(String ownerId, String providerId) {
        return vault.usableKeys(ownerId, providerId);
    }

    @Override
    public boolean hasKeys(String ownerId, String providerId) {
        return !vault.usableKeys(ownerId, providerId).isEmpty();
    }

    @Override
    public void reportFailure(String ownerId, String providerId, String key) {
        vault.markFailure(ownerId, providerId, key);
    }

    @Override
    public void reportSuccess(String ownerId, String providerId, String key) {
        vault.markSuccess(ownerId, providerId, key);
    }

    @Override
    public boolean isKeyless(String providerId) {
        return !ProviderCatalog.get(providerId).isRequiresKey();
    }
}
