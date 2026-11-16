package ai.indra.backend.store;

import ai.indra.core.apikeys.KeyStore;
import ai.indra.core.model.ApiKeyEntry;

import java.util.List;

/** Adapts {@link Repository} to the core API-key vault contract. */
public final class RepositoryKeyStore implements KeyStore {

    private final Repository repository;

    public RepositoryKeyStore(Repository repository) {
        this.repository = repository;
    }

    @Override
    public List<ApiKeyEntry> loadEntries(String ownerId) {
        return repository.loadApiKeys(ownerId);
    }

    @Override
    public void saveEntry(String ownerId, ApiKeyEntry entry) {
        repository.saveApiKey(ownerId, entry);
    }

    @Override
    public void deleteEntry(String ownerId, String entryId) {
        repository.deleteApiKey(ownerId, entryId);
    }

    @Override
    public void clearOwner(String ownerId) {
        repository.clearApiKeys(ownerId);
    }

    @Override
    public void saveSecret(String ownerId, String entryId, String encryptedSecret) {
        repository.saveApiKeySecret(ownerId, entryId, encryptedSecret);
    }

    @Override
    public String loadSecret(String entryId) {
        return repository.loadApiKeySecret(entryId);
    }

    @Override
    public void deleteSecret(String entryId) {
        repository.deleteApiKeySecret(entryId);
    }
}
