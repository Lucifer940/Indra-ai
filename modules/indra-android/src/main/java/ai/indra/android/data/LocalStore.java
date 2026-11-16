package ai.indra.android.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import ai.indra.core.apikeys.KeyStore;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.ApiKeyEntry;
import ai.indra.core.model.ChatMessage;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.model.TaskItem;
import ai.indra.core.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Private local database: memories, tasks, message cache, API key metadata and a
 * small key/value store for UI state.
 *
 * <p>Everything lives in the app's sandbox and is excluded from Android backups
 * ({@code backup_rules.xml}), so it is removed with the app and never shared with
 * other apps.</p>
 */
public final class LocalStore extends SQLiteOpenHelper implements KeyStore {

    private static final String NAME = "indra_local.db";
    private static final int VERSION = 1;

    public LocalStore(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memories (id TEXT PRIMARY KEY, doc TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_memories_updated ON memories(updated_at)");
        db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, doc TEXT NOT NULL, completed INTEGER NOT NULL, "
                + "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY, conversation_id TEXT, created_at INTEGER NOT NULL, "
                + "doc TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at)");
        db.execSQL("CREATE TABLE api_keys (id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, doc TEXT NOT NULL)");
        db.execSQL("CREATE TABLE api_secrets (entry_id TEXT PRIMARY KEY, secret TEXT NOT NULL)");
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.info("LocalStore", "Upgrading local database " + oldVersion + " → " + newVersion);
        // Version 1 is the first schema; future migrations are additive.
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ------------------------------------------------------------------ memory

    public void saveMemories(List<MemoryItem> items) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (MemoryItem item : items) {
                ContentValues values = new ContentValues();
                values.put("id", item.getId());
                values.put("doc", item.toJson().toJson());
                values.put("updated_at", item.getUpdatedAt() > 0 ? item.getUpdatedAt() : System.currentTimeMillis());
                db.insertWithOnConflict("memories", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<MemoryItem> memories() {
        List<MemoryItem> items = new ArrayList<MemoryItem>();
        Cursor cursor = getReadableDatabase().query("memories", new String[]{"doc"},
                null, null, null, null, "updated_at DESC");
        try {
            while (cursor.moveToNext()) {
                try {
                    items.add(MemoryItem.fromJson(JsonObject.parse(cursor.getString(0))));
                } catch (RuntimeException error) {
                    Log.warn("LocalStore", "Skipping unreadable memory row: " + error.getMessage());
                }
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public void deleteMemory(String id) {
        getWritableDatabase().delete("memories", "id = ?", new String[]{id});
    }

    public void clearMemories() {
        getWritableDatabase().delete("memories", null, null);
    }

    // ------------------------------------------------------------------- tasks

    public void saveTasks(List<TaskItem> tasks) {
        for (TaskItem task : tasks) {
            saveTask(task);
        }
    }

    public void saveTask(TaskItem task) {
        ContentValues values = new ContentValues();
        values.put("id", task.getId());
        values.put("doc", task.toJson().toJson());
        values.put("completed", task.isCompleted() ? 1 : 0);
        values.put("updated_at", task.getUpdatedAt() > 0 ? task.getUpdatedAt() : System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<TaskItem> tasks(boolean includeCompleted) {
        List<TaskItem> items = new ArrayList<TaskItem>();
        String selection = includeCompleted ? null : "completed = 0";
        Cursor cursor = getReadableDatabase().query("tasks", new String[]{"doc"}, selection, null,
                null, null, "updated_at DESC");
        try {
            while (cursor.moveToNext()) {
                try {
                    items.add(TaskItem.fromJson(JsonObject.parse(cursor.getString(0))));
                } catch (RuntimeException error) {
                    Log.warn("LocalStore", "Skipping unreadable task row: " + error.getMessage());
                }
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public void deleteTask(String id) {
        getWritableDatabase().delete("tasks", "id = ?", new String[]{id});
    }

    // ---------------------------------------------------------------- messages

    public void saveMessage(ChatMessage message) {
        ContentValues values = new ContentValues();
        values.put("id", message.getId());
        values.put("conversation_id", message.getConversationId());
        values.put("created_at", message.getCreatedAt() > 0 ? message.getCreatedAt() : System.currentTimeMillis());
        values.put("doc", message.toJson().toJson());
        getWritableDatabase().insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Most recent messages for a conversation, oldest first. */
    public List<ChatMessage> messages(String conversationId, int limit) {
        List<ChatMessage> items = new ArrayList<ChatMessage>();
        String sql = "SELECT doc FROM (SELECT doc, created_at FROM messages WHERE conversation_id "
                + (conversationId == null ? "IS NULL" : "= ?")
                + " ORDER BY created_at DESC LIMIT ?) ORDER BY created_at ASC";
        String[] args = conversationId == null
                ? new String[]{String.valueOf(limit)}
                : new String[]{conversationId, String.valueOf(limit)};
        Cursor cursor = getReadableDatabase().rawQuery(sql, args);
        try {
            while (cursor.moveToNext()) {
                try {
                    items.add(ChatMessage.fromJson(JsonObject.parse(cursor.getString(0))));
                } catch (RuntimeException error) {
                    Log.warn("LocalStore", "Skipping unreadable message row: " + error.getMessage());
                }
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public void clearMessages() {
        getWritableDatabase().delete("messages", null, null);
    }

    // ------------------------------------------------- API keys (core KeyStore)

    @Override
    public List<ApiKeyEntry> loadEntries(String ownerId) {
        List<ApiKeyEntry> entries = new ArrayList<ApiKeyEntry>();
        Cursor cursor = getReadableDatabase().query("api_keys", new String[]{"doc"}, "owner_id = ?",
                new String[]{ownerId}, null, null, null);
        try {
            while (cursor.moveToNext()) {
                try {
                    entries.add(ApiKeyEntry.fromJson(JsonObject.parse(cursor.getString(0))));
                } catch (RuntimeException error) {
                    Log.warn("LocalStore", "Skipping unreadable key row: " + error.getMessage());
                }
            }
        } finally {
            cursor.close();
        }
        return entries;
    }

    @Override
    public void saveEntry(String ownerId, ApiKeyEntry entry) {
        ContentValues values = new ContentValues();
        values.put("id", entry.getId());
        values.put("owner_id", ownerId);
        values.put("doc", entry.toJson().toJson());
        getWritableDatabase().insertWithOnConflict("api_keys", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public void deleteEntry(String ownerId, String entryId) {
        getWritableDatabase().delete("api_keys", "id = ? AND owner_id = ?", new String[]{entryId, ownerId});
        deleteSecret(entryId);
    }

    @Override
    public void clearOwner(String ownerId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.query("api_keys", new String[]{"id"}, "owner_id = ?", new String[]{ownerId},
                null, null, null);
        try {
            while (cursor.moveToNext()) {
                deleteSecret(cursor.getString(0));
            }
        } finally {
            cursor.close();
        }
        db.delete("api_keys", "owner_id = ?", new String[]{ownerId});
    }

    @Override
    public void saveSecret(String ownerId, String entryId, String encryptedSecret) {
        ContentValues values = new ContentValues();
        values.put("entry_id", entryId);
        values.put("secret", encryptedSecret);
        getWritableDatabase().insertWithOnConflict("api_secrets", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public String loadSecret(String entryId) {
        Cursor cursor = getReadableDatabase().query("api_secrets", new String[]{"secret"}, "entry_id = ?",
                new String[]{entryId}, null, null, null);
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    @Override
    public void deleteSecret(String entryId) {
        getWritableDatabase().delete("api_secrets", "entry_id = ?", new String[]{entryId});
    }

    // --------------------------------------------------------- key/value state

    public void putJson(String key, JsonObject value) {
        ContentValues values = new ContentValues();
        values.put("k", key);
        values.put("v", value == null ? "{}" : value.toJson());
        getWritableDatabase().insertWithOnConflict("kv", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public JsonObject getJson(String key) {
        Cursor cursor = getReadableDatabase().query("kv", new String[]{"v"}, "k = ?",
                new String[]{key}, null, null, null);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return JsonObject.parseOrEmpty(cursor.getString(0));
        } catch (RuntimeException error) {
            return null;
        } finally {
            cursor.close();
        }
    }

    public void remove(String key) {
        getWritableDatabase().delete("kv", "k = ?", new String[]{key});
    }
}
