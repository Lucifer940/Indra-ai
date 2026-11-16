package ai.indra.android.platform;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;

import ai.indra.core.util.Log;
import ai.indra.core.util.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional contact lookup. Used only when the user asks to call or message a
 * named person and has granted the contacts permission. Nothing is uploaded.
 */
public final class Contacts {

    /** A matched contact with all known numbers. */
    public static final class Match {
        public final String name;
        public final List<String> numbers;

        Match(String name, List<String> numbers) {
            this.name = name;
            this.numbers = numbers;
        }
    }

    private Contacts() {
    }

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Finds the best contact whose display name contains the query. */
    public static Match find(Context context, String query) {
        if (!hasPermission(context) || query == null || query.trim().isEmpty()) {
            return null;
        }
        String needle = Strings.lower(query).trim();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, null);
            if (cursor == null) {
                return null;
            }
            String bestName = null;
            List<String> numbers = new ArrayList<String>();
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String number = cursor.getString(1);
                if (name == null || number == null) {
                    continue;
                }
                String lower = Strings.lower(name);
                if (lower.equals(needle) || lower.contains(needle)) {
                    if (bestName == null || lower.equals(needle)) {
                        if (bestName != null && !bestName.equalsIgnoreCase(name)) {
                            numbers.clear();
                        }
                        bestName = name;
                    }
                    if (name.equalsIgnoreCase(bestName) && !numbers.contains(number)) {
                        numbers.add(number);
                    }
                }
            }
            return bestName == null || numbers.isEmpty() ? null : new Match(bestName, numbers);
        } catch (RuntimeException error) {
            Log.warn("Contacts", "Contact lookup failed: " + error.getMessage());
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /** True when the text is already a phone number. */
    public static boolean looksLikeNumber(String text) {
        if (text == null) {
            return false;
        }
        String digits = text.replaceAll("[^0-9]", "");
        return digits.length() >= 5 && !text.matches(".*[A-Za-z].*");
    }
}
