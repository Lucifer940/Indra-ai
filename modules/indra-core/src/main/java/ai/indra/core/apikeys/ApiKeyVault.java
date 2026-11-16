package ai.indra.core.apikeys;

import ai.indra.core.ai.ProviderCatalog;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.ApiKeyEntry;
import ai.indra.core.security.Crypto;
import ai.indra.core.util.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Multi-key vault. Users can store several keys per provider; INDRA rotates to
 * the next healthy key when one is rate limited or rejected. Keys are stored
 * encrypted through {@link SecretCipher} and only ever displayed masked.
 */
public final class ApiKeyVault {

    private static final long COOLDOWN_STEP_MS = 30L * 1000L;
    private static final long MAX_COOLDOWN_MS = 60L * 60L * 1000L;

    private final KeyStore store;
    private final SecretCipher cipher;

    public ApiKeyVault(KeyStore store, SecretCipher cipher) {
        if (store == null || cipher == null) {
            throw new IllegalArgumentException("KeyStore and SecretCipher are required");
        }
        this.store = store;
        this.cipher = cipher;
    }

    /** Adds (or updates) a key and returns its metadata. */
    public ApiKeyEntry addKey(String ownerId, String providerId, String label, String secret, boolean permanent) {
        String provider = providerId == null ? "" : providerId.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ProviderCatalog.exists(provider) || !ProviderCatalog.get(provider).isRequiresKey()) {
            throw new IllegalArgumentException("Unsupported provider: " + providerId);
        }
        String key = Strings.trimToNull(secret);
        if (key == null || key.length() < 8) {
            throw new IllegalArgumentException("API key looks too short");
        }
        List<ApiKeyEntry> existing = store.loadEntries(ownerId);
        for (ApiKeyEntry entry : existing) {
            if (provider.equals(entry.getProvider())) {
                String stored = store.loadSecret(entry.getId());
                if (stored != null) {
                    try {
                        if (Crypto.constantTimeEquals(cipher.decrypt(stored), key)) {
                            return entry; // already stored
                        }
                    } catch (RuntimeException ignored) {
                        // corrupted entry - will be replaced below
                    }
                }
            }
        }
        ApiKeyEntry entry = ApiKeyEntry.create(ownerId, provider, Strings.isBlank(label) ? defaultLabel(existing, provider) : label.trim(),
                Strings.maskSecret(key));
        entry.setPermanent(permanent);
        store.saveEntry(ownerId, entry);
        store.saveSecret(ownerId, entry.getId(), cipher.encrypt(key));
        return entry;
    }

    private String defaultLabel(List<ApiKeyEntry> existing, String provider) {
        int count = 0;
        for (ApiKeyEntry entry : existing) {
            if (provider.equals(entry.getProvider())) {
                count++;
            }
        }
        return ProviderCatalog.get(provider).getLabel() + " key " + (count + 1);
    }

    public List<ApiKeyEntry> entries(String ownerId) {
        List<ApiKeyEntry> entries = new ArrayList<ApiKeyEntry>(store.loadEntries(ownerId));
        Collections.sort(entries, new Comparator<ApiKeyEntry>() {
            @Override
            public int compare(ApiKeyEntry left, ApiKeyEntry right) {
                int byProvider = Strings.orEmpty(left.getProvider()).compareTo(Strings.orEmpty(right.getProvider()));
                if (byProvider != 0) {
                    return byProvider;
                }
                return Long.compare(left.getCreatedAt(), right.getCreatedAt());
            }
        });
        return entries;
    }

    public List<ApiKeyEntry> entries(String ownerId, String providerId) {
        List<ApiKeyEntry> result = new ArrayList<ApiKeyEntry>();
        for (ApiKeyEntry entry : entries(ownerId)) {
            if (providerId != null && providerId.equalsIgnoreCase(entry.getProvider())) {
                result.add(entry);
            }
        }
        return result;
    }

    public boolean removeKey(String ownerId, String entryId) {
        for (ApiKeyEntry entry : store.loadEntries(ownerId)) {
            if (entry.getId().equals(entryId)) {
                store.deleteSecret(entryId);
                store.deleteEntry(ownerId, entryId);
                return true;
            }
        }
        return false;
    }

    public void setActive(String ownerId, String entryId, boolean active) {
        for (ApiKeyEntry entry : store.loadEntries(ownerId)) {
            if (entry.getId().equals(entryId)) {
                entry.setActive(active);
                store.saveEntry(ownerId, entry);
                return;
            }
        }
    }

    /** "Reset" in Settings: forgets every key and secret for this user/device. */
    public int resetAll(String ownerId) {
        int count = 0;
        for (ApiKeyEntry entry : store.loadEntries(ownerId)) {
            store.deleteSecret(entry.getId());
            count++;
        }
        store.clearOwner(ownerId);
        return count;
    }

    public int resetProvider(String ownerId, String providerId) {
        int count = 0;
        for (ApiKeyEntry entry : entries(ownerId, providerId)) {
            store.deleteSecret(entry.getId());
            store.deleteEntry(ownerId, entry.getId());
            count++;
        }
        return count;
    }

    /** All usable keys for a provider, healthiest first. */
    public List<String> usableKeys(String ownerId, String providerId) {
        List<ApiKeyEntry> candidates = entries(ownerId, providerId);
        long now = System.currentTimeMillis();
        Collections.sort(candidates, new Comparator<ApiKeyEntry>() {
            @Override
            public int compare(ApiKeyEntry left, ApiKeyEntry right) {
                int byFailures = Integer.compare(left.getFailureCount(), right.getFailureCount());
                if (byFailures != 0) {
                    return byFailures;
                }
                int byCooldown = Long.compare(left.getCooldownUntil(), right.getCooldownUntil());
                if (byCooldown != 0) {
                    return byCooldown;
                }
                return Long.compare(right.getLastUsedAt(), left.getLastUsedAt());
            }
        });
        List<String> keys = new ArrayList<String>();
        for (ApiKeyEntry entry : candidates) {
            if (!entry.isUsable(now)) {
                continue;
            }
            String encrypted = store.loadSecret(entry.getId());
            if (encrypted == null) {
                continue;
            }
            try {
                keys.add(cipher.decrypt(encrypted));
            } catch (RuntimeException error) {
                ai.indra.core.util.Log.warn("ApiKeyVault", "Could not decrypt key " + entry.getId());
            }
        }
        return keys;
    }

    /** Records a failure and puts the key in exponential cooldown. */
    public void markFailure(String ownerId, String providerId, String secret) {
        for (ApiKeyEntry entry : entries(ownerId, providerId)) {
            String encrypted = store.loadSecret(entry.getId());
            if (encrypted == null) {
                continue;
            }
            try {
                if (!Crypto.constantTimeEquals(cipher.decrypt(encrypted), secret)) {
                    continue;
                }
            } catch (RuntimeException error) {
                continue;
            }
            int failures = entry.getFailureCount() + 1;
            entry.setFailureCount(failures);
            long cooldown = Math.min(MAX_COOLDOWN_MS, COOLDOWN_STEP_MS * (1L << Math.min(failures - 1, 7)));
            entry.setCooldownUntil(System.currentTimeMillis() + cooldown);
            store.saveEntry(ownerId, entry);
            return;
        }
    }

    public void markSuccess(String ownerId, String providerId, String secret) {
        for (ApiKeyEntry entry : entries(ownerId, providerId)) {
            String encrypted = store.loadSecret(entry.getId());
            if (encrypted == null) {
                continue;
            }
            try {
                if (!Crypto.constantTimeEquals(cipher.decrypt(encrypted), secret)) {
                    continue;
                }
            } catch (RuntimeException error) {
                continue;
            }
            entry.setFailureCount(0);
            entry.setCooldownUntil(0L);
            entry.setLastUsedAt(System.currentTimeMillis());
            store.saveEntry(ownerId, entry);
            return;
        }
    }

    /** Settings-screen view: masked keys only, never secrets. */
    public JsonArray describe(String ownerId) {
        JsonArray array = new JsonArray();
        for (ApiKeyEntry entry : entries(ownerId)) {
            JsonObject json = entry.toJson();
            json.put("providerLabel", ProviderCatalog.get(entry.getProvider()).getLabel());
            array.add(json);
        }
        return array;
    }

    public boolean hasAnyKey(String ownerId) {
        return !entries(ownerId).isEmpty();
    }
}
