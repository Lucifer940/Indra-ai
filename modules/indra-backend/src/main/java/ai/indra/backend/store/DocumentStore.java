package ai.indra.backend.store;

import ai.indra.core.json.JsonObject;

import java.util.List;

/**
 * Persistence abstraction. Documents are JSON, which keeps migrations simple and
 * lets the same code run on JSON files (default, zero setup) or SQL.
 */
public interface DocumentStore {

    void init();

    void close();

    JsonObject get(String collection, String id);

    /**
     * Inserts or replaces a document.
     *
     * @param lookupKey optional indexed key (for example a lower-cased email)
     */
    void put(String collection, String id, String userId, String lookupKey, long updatedAt, JsonObject document);

    void delete(String collection, String id);

    /**
     * Lists documents, optionally filtered by owner and change cursor.
     *
     * @param userId       null for all users
     * @param updatedAfter only documents changed strictly after this time (0 = all)
     */
    List<JsonObject> list(String collection, String userId, long updatedAfter, int limit);

    JsonObject findByLookup(String collection, String lookupKey);

    int count(String collection);

    /** Removes every document belonging to a user (account deletion). */
    void deleteUserData(String userId);

    /** Human readable storage description for the health endpoint. */
    String describe();
}
