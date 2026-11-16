package ai.indra.android.platform;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Permission-friendly file helpers: exports go to the app cache and are shared
 * through the FileProvider, so INDRA never needs broad storage access.
 */
public final class FileExport {

    private static final int MAX_READ_BYTES = 512 * 1024;

    private FileExport() {
    }

    public static File exportJson(Context context, String name, JsonObject content) {
        File directory = new File(context.getCacheDir(), "exports");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.warn("FileExport", "Cannot create exports directory");
        }
        File target = new File(directory, sanitize(name) + ".json");
        try {
            FileOutputStream out = new FileOutputStream(target);
            out.write(content.toPrettyJson().getBytes(StandardCharsets.UTF_8));
            out.close();
            return target;
        } catch (IOException error) {
            Log.error("FileExport", "Export failed", error);
            return null;
        }
    }

    public static void shareFile(Context context, File file, String mimeType) {
        if (file == null || !file.exists()) {
            return;
        }
        Uri uri = androidx.core.content.FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(intent, "Share INDRA export"));
    }

    public static File saveDrawing(Context context, Bitmap bitmap, String name) {
        File directory = new File(context.getFilesDir(), "drawings");
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }
        File target = new File(directory, sanitize(name) + ".png");
        try {
            FileOutputStream out = new FileOutputStream(target);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return target;
        } catch (IOException error) {
            Log.error("FileExport", "Drawing save failed", error);
            return null;
        }
    }

    /** Reads a user-selected text/document file with a hard size cap. */
    public static String readText(Context context, Uri uri) {
        try {
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            StringBuilder text = new StringBuilder();
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_READ_BYTES) {
                    text.append("\n\n[truncated - file larger than 512 KB]");
                    break;
                }
                text.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            input.close();
            return text.toString();
        } catch (IOException error) {
            Log.warn("FileExport", "Cannot read file: " + error.getMessage());
            return null;
        }
    }

    public static String fileName(Context context, Uri uri) {
        try {
            android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor == null) {
                return "file";
            }
            try {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (cursor.moveToFirst() && index >= 0) {
                    return cursor.getString(index);
                }
            } finally {
                cursor.close();
            }
        } catch (RuntimeException error) {
            Log.warn("FileExport", "Cannot resolve file name");
        }
        return "file";
    }

    private static String sanitize(String name) {
        String cleaned = name == null ? "export" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return String.format(Locale.US, "%s_%d", cleaned, System.currentTimeMillis());
    }
}
