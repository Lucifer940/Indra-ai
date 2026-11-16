package ai.indra.core.apikeys;

import ai.indra.core.model.ApiKeyEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyVaultTest {

    /** Simple in-memory store used only by tests. */
    static final class MemoryKeyStore implements KeyStore {
        final Map<String, List<ApiKeyEntry>> entries = new HashMap<String, List<ApiKeyEntry>>();
        final Map<String, String> secrets = new HashMap<String, String>();

        @Override
        public List<ApiKeyEntry> loadEntries(String ownerId) {
            List<ApiKeyEntry> list = entries.get(ownerId);
            return list == null ? new ArrayList<ApiKeyEntry>() : new ArrayList<ApiKeyEntry>(list);
        }

        @Override
        public void saveEntry(String ownerId, ApiKeyEntry entry) {
            List<ApiKeyEntry> list = entries.get(ownerId);
            if (list == null) {
                list = new ArrayList<ApiKeyEntry>();
                entries.put(ownerId, list);
            }
            list.removeIf(existing -> existing.getId().equals(entry.getId()));
            list.add(entry);
        }

        @Override
        public void deleteEntry(String ownerId, String entryId) {
            List<ApiKeyEntry> list = entries.get(ownerId);
            if (list != null) {
                list.removeIf(existing -> existing.getId().equals(entryId));
            }
        }

        @Override
        public void clearOwner(String ownerId) {
            entries.remove(ownerId);
        }

        @Override
        public void saveSecret(String ownerId, String entryId, String encryptedSecret) {
            secrets.put(entryId, encryptedSecret);
        }

        @Override
        public String loadSecret(String entryId) {
            return secrets.get(entryId);
        }

        @Override
        public void deleteSecret(String entryId) {
            secrets.remove(entryId);
        }
    }

    /** Reversible test cipher - real clients use Keystore/DPAPI encryption. */
    static final class TestCipher implements SecretCipher {
        @Override
        public String encrypt(String plaintext) {
            return "enc:" + new StringBuilder(plaintext).reverse();
        }

        @Override
        public String decrypt(String cipherText) {
            String body = cipherText.startsWith("enc:") ? cipherText.substring(4) : cipherText;
            return new StringBuilder(body).reverse().toString();
        }
    }

    private ApiKeyVault vault(MemoryKeyStore store) {
        return new ApiKeyVault(store, new TestCipher());
    }

    @Test
    void storesMultipleKeysPerProviderMasked() {
        MemoryKeyStore store = new MemoryKeyStore();
        ApiKeyVault vault = vault(store);

        ApiKeyEntry first = vault.addKey("u1", "openai", "work key", "sk-firstkey1234567890", true);
        ApiKeyEntry second = vault.addKey("u1", "openai", "home key", "sk-secondkey0987654321", true);

        assertEquals(2, vault.entries("u1", "openai").size());
        assertTrue(first.getMaskedKey().contains("..."));
        assertFalse(first.getMaskedKey().contains("firstkey1234567890"));
        assertNotEquals(first.getId(), second.getId());
        assertTrue(second.isPermanent());
        assertEquals(2, vault.usableKeys("u1", "openai").size());
    }

    @Test
    void duplicateKeyIsNotStoredTwice() {
        ApiKeyVault vault = vault(new MemoryKeyStore());
        vault.addKey("u1", "deepseek", null, "sk-abcdefghijkl", true);
        vault.addKey("u1", "deepseek", null, "sk-abcdefghijkl", true);
        assertEquals(1, vault.entries("u1", "deepseek").size());
    }

    @Test
    void rejectsUnknownProviderAndShortKey() {
        ApiKeyVault vault = vault(new MemoryKeyStore());
        assertThrows(IllegalArgumentException.class, () -> vault.addKey("u1", "not-a-provider", null, "sk-1234567890", true));
        assertThrows(IllegalArgumentException.class, () -> vault.addKey("u1", "openai", null, "short", true));
        // Providers that need no key cannot have one added.
        assertThrows(IllegalArgumentException.class, () -> vault.addKey("u1", "ollama", null, "sk-1234567890", true));
    }

    @Test
    void failurePutsKeyInCooldownAndRotationSkipsIt() {
        ApiKeyVault vault = vault(new MemoryKeyStore());
        vault.addKey("u1", "openai", "one", "sk-111111111111", true);
        vault.addKey("u1", "openai", "two", "sk-222222222222", true);

        List<String> before = vault.usableKeys("u1", "openai");
        assertEquals(2, before.size());

        vault.markFailure("u1", "openai", before.get(0));
        List<String> after = vault.usableKeys("u1", "openai");
        assertEquals(1, after.size());
        assertEquals(before.get(1), after.get(0));

        vault.markSuccess("u1", "openai", before.get(0));
        assertEquals(2, vault.usableKeys("u1", "openai").size());
    }

    @Test
    void deactivateHidesKeyFromRotation() {
        ApiKeyVault vault = vault(new MemoryKeyStore());
        ApiKeyEntry entry = vault.addKey("u1", "groq", null, "gsk-abcdefghijkl", true);
        vault.setActive("u1", entry.getId(), false);
        assertEquals(0, vault.usableKeys("u1", "groq").size());
        assertEquals(1, vault.entries("u1").size());
    }

    @Test
    void resetAllRemovesKeysAndSecrets() {
        MemoryKeyStore store = new MemoryKeyStore();
        ApiKeyVault vault = vault(store);
        vault.addKey("u1", "openai", null, "sk-abcdefghijkl", true);
        vault.addKey("u1", "google", null, "AIzaabcdefghij", true);

        assertEquals(2, vault.resetAll("u1"));
        assertEquals(0, vault.entries("u1").size());
        assertTrue(store.secrets.isEmpty());
        assertFalse(vault.hasAnyKey("u1"));
    }

    @Test
    void removeSingleKeyDeletesSecret() {
        MemoryKeyStore store = new MemoryKeyStore();
        ApiKeyVault vault = vault(store);
        ApiKeyEntry entry = vault.addKey("u1", "anthropic", null, "sk-ant-abcdefghij", true);
        assertTrue(vault.removeKey("u1", entry.getId()));
        assertFalse(vault.removeKey("u1", entry.getId()));
        assertTrue(store.secrets.isEmpty());
    }

    @Test
    void descriptionNeverLeaksSecrets() {
        ApiKeyVault vault = vault(new MemoryKeyStore());
        vault.addKey("u1", "openrouter", "main", "sk-or-verysecretvalue123", true);
        String described = vault.describe("u1").toJson();
        assertFalse(described.contains("verysecretvalue123"));
        assertTrue(described.contains("openrouter"));
        assertNotNull(described);
    }

    @Test
    void entriesAreIsolatedPerOwner() {
        ApiKeyVault vault = vault(new MemoryKeyStore());
        vault.addKey("owner-a", "openai", null, "sk-aaaaaaaaaaaa", true);
        assertEquals(1, vault.entries("owner-a").size());
        assertEquals(0, vault.entries("owner-b").size());
    }
}
