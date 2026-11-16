package ai.indra.android.data;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import ai.indra.android.IndraApp;

import java.util.Locale;
import java.util.TimeZone;

/** Stable device identity used for pairing and sync. */
public final class DeviceIdentity {

    private DeviceIdentity() {
    }

    public static String name(Context context) {
        Prefs prefs = IndraApp.from(context).prefs();
        String stored = prefs.getDeviceName();
        if (stored != null && !stored.trim().isEmpty()) {
            return stored;
        }
        return (Build.MANUFACTURER + " " + Build.MODEL).trim();
    }

    public static String platform() {
        return "ANDROID";
    }

    public static String id(Context context) {
        return IndraApp.from(context).prefs().getDeviceId();
    }

    public static String timeZone() {
        return TimeZone.getDefault().getID();
    }

    /** Non-personal install identifier used only for diagnostics. */
    public static String installId(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return androidId == null ? "unknown" : androidId.toLowerCase(Locale.ROOT);
    }
}
