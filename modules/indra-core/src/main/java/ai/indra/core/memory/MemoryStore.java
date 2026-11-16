package ai.indra.core.memory;

import ai.indra.core.model.MemoryItem;

import java.util.List;

/** Persistence contract for long-term memory (implemented by each client and the backend). */
public interface MemoryStore {

    List<MemoryItem> listByUser(String userId);

    MemoryItem findById(String userId, String id);

    MemoryItem save(MemoryItem item);

    /** Soft-deletes so deletions can sync to other devices. */
    void delete(String userId, String id);

    /** Permanently removes every memory for the user. */
    void purge(String userId);
}
