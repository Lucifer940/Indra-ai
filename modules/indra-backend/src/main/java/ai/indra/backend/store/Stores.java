package ai.indra.backend.store;

import ai.indra.backend.BackendConfig;
import ai.indra.core.util.Log;

import java.io.File;

/** Chooses the storage engine based on configuration. */
public final class Stores {

    private Stores() {
    }

    public static DocumentStore create(BackendConfig config) {
        return create(config, FileCodec.PLAIN);
    }

    /** Creates the store, optionally encrypting every document at rest. */
    public static DocumentStore create(BackendConfig config, FileCodec codec) {
        String url = config.getDatabaseUrl();
        if (url != null && !url.trim().isEmpty()) {
            JdbcStore store = new JdbcStore(url, config.getDatabaseUser(), config.getDatabasePassword());
            store.init();
            Log.info("Stores", "Using SQL storage: " + store.describe());
            return store;
        }
        EmbeddedStore store = new EmbeddedStore(new File(config.getDataDir(), "store"), codec);
        store.init();
        return store;
    }
}
