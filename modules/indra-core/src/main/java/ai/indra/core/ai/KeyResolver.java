package ai.indra.core.ai;

import java.util.List;

/** Supplies provider keys for a user and records their health. */
public interface KeyResolver {

    List<String> keysFor(String ownerId, String providerId);

    boolean hasKeys(String ownerId, String providerId);

    void reportFailure(String ownerId, String providerId, String key);

    void reportSuccess(String ownerId, String providerId, String key);

    /** True when the provider runs locally and needs no key. */
    boolean isKeyless(String providerId);
}
