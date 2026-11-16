package ai.indra.backend.store;

import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL-backed document store for SQLite and PostgreSQL.
 *
 * <p>Documents are stored as JSON in a single indexed table, which keeps the
 * schema stable while models evolve. Indexes on {@code (collection, user_id,
 * updated_at)} and {@code (collection, lookup_key)} keep the common queries fast.
 * The DDL below is the portable subset accepted by both engines; the same
 * statements exist in {@code db/migrations/V1__init.sql}.</p>
 */
public final class JdbcStore implements DocumentStore {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS indra_documents ("
            + "collection VARCHAR(64) NOT NULL,"
            + "id VARCHAR(64) NOT NULL,"
            + "user_id VARCHAR(64),"
            + "lookup_key VARCHAR(255),"
            + "updated_at BIGINT NOT NULL,"
            + "doc TEXT NOT NULL,"
            + "PRIMARY KEY (collection, id))";

    private static final String[] CREATE_INDEXES = {
        "CREATE INDEX IF NOT EXISTS idx_indra_docs_user ON indra_documents (collection, user_id, updated_at)",
        "CREATE INDEX IF NOT EXISTS idx_indra_docs_lookup ON indra_documents (collection, lookup_key)"
    };

    private final String url;
    private final String user;
    private final String password;

    public JdbcStore(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection connection() throws SQLException {
        if (user == null || user.isEmpty()) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public void init() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            for (String index : CREATE_INDEXES) {
                statement.execute(index);
            }
            Log.info("JdbcStore", "Connected to " + safeUrl());
        } catch (SQLException error) {
            throw new IllegalStateException("Cannot initialise database at " + safeUrl() + ": " + error.getMessage(), error);
        }
    }

    @Override
    public void close() {
        // Connections are opened per operation, so nothing to close.
    }

    @Override
    public JsonObject get(String collection, String id) {
        String sql = "SELECT doc FROM indra_documents WHERE collection = ? AND id = ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collection);
            statement.setString(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return JsonObject.parse(rows.getString(1));
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Read failed: " + error.getMessage(), error);
        }
        return null;
    }

    @Override
    public void put(String collection, String id, String userId, String lookupKey, long updatedAt, JsonObject document) {
        String delete = "DELETE FROM indra_documents WHERE collection = ? AND id = ?";
        String insert = "INSERT INTO indra_documents (collection, id, user_id, lookup_key, updated_at, doc) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(delete)) {
                    statement.setString(1, collection);
                    statement.setString(2, id);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, collection);
                    statement.setString(2, id);
                    statement.setString(3, userId);
                    statement.setString(4, lookupKey);
                    statement.setLong(5, updatedAt);
                    statement.setString(6, document.toJson());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Write failed: " + error.getMessage(), error);
        }
    }

    @Override
    public void delete(String collection, String id) {
        String sql = "DELETE FROM indra_documents WHERE collection = ? AND id = ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collection);
            statement.setString(2, id);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Delete failed: " + error.getMessage(), error);
        }
    }

    @Override
    public List<JsonObject> list(String collection, String userId, long updatedAfter, int limit) {
        StringBuilder sql = new StringBuilder("SELECT doc FROM indra_documents WHERE collection = ?");
        if (userId != null) {
            sql.append(" AND user_id = ?");
        }
        if (updatedAfter > 0L) {
            sql.append(" AND updated_at > ?");
        }
        sql.append(" ORDER BY updated_at ASC");
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        List<JsonObject> result = new ArrayList<JsonObject>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setString(index++, collection);
            if (userId != null) {
                statement.setString(index++, userId);
            }
            if (updatedAfter > 0L) {
                statement.setLong(index++, updatedAfter);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(JsonObject.parse(rows.getString(1)));
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("List failed: " + error.getMessage(), error);
        }
        return result;
    }

    @Override
    public JsonObject findByLookup(String collection, String lookupKey) {
        if (lookupKey == null) {
            return null;
        }
        String sql = "SELECT doc FROM indra_documents WHERE collection = ? AND lookup_key = ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collection);
            statement.setString(2, lookupKey);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return JsonObject.parse(rows.getString(1));
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Lookup failed: " + error.getMessage(), error);
        }
        return null;
    }

    @Override
    public int count(String collection) {
        String sql = "SELECT COUNT(*) FROM indra_documents WHERE collection = ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collection);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Count failed: " + error.getMessage(), error);
        }
    }

    @Override
    public void deleteUserData(String userId) {
        String sql = "DELETE FROM indra_documents WHERE user_id = ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("User purge failed: " + error.getMessage(), error);
        }
    }

    @Override
    public String describe() {
        return "jdbc:" + safeUrl();
    }

    private String safeUrl() {
        return url.replaceAll("password=[^&;]*", "password=***");
    }
}
