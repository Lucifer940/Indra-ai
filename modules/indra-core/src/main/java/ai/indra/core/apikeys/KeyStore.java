package ai.indra.core.apikeys;

import ai.indra.core.model.ApiKeyEntry;

import java.util.List;

/** Persistence contract for API key metadata and encrypted secrets. */
public interface KeyStore {

    List<ApiKeyEntry> loadEntries(String ownerId);

    void saveEntry(String ownerId, ApiKeyEntry entry);

    void deleteEntry(String ownerId, String entryId);

    void clearOwner(String ownerId);

    void saveSecret(String ownerId, String entryId, String encryptedSecret);

    String loadSecret(String entryId);

    void deleteSecret(String entryId);
}
