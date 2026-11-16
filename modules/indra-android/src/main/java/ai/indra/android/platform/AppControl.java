package ai.indra.android.platform;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.SmsManager;

import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.util.Log;
import ai.indra.core.util.Strings;

import java.util.List;
import java.util.Locale;

/**
 * Permission-aware phone control. Every sensitive action is performed only after
 * the user confirms it in the app (the UI always shows a confirmation dialog),
 * and nothing bypasses Android's own permission model.
 */
public final class AppControl {

    /** Result of an attempted action. */
    public static final class Result {
        public final boolean success;
        public final String message;

        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private AppControl() {
    }

    /** Opens an installed app by (partial) name. */
    public static Result openApp(Context context, String appName) {
        String needle = Strings.lower(appName).replace(" app", "").trim();
        PackageManager manager = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = manager.queryIntentActivities(launcher, 0);
        for (ResolveInfo info : apps) {
            String label = Strings.lower(String.valueOf(info.loadLabel(manager)));
            if (label.equals(needle) || label.contains(needle)) {
                Intent intent = manager.getLaunchIntentForPackage(info.activityInfo.packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return new Result(true, "Opening " + info.loadLabel(manager));
                }
            }
        }
        // Fall back to a Play Store search so the user still gets somewhere useful.
        try {
            Intent store = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(appName) + "&c=apps"));
            store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(store);
            return new Result(false, "I couldn't find \"" + appName + "\" installed - opened Play Store search");
        } catch (RuntimeException error) {
            return new Result(false, "No app found for \"" + appName + "\"");
        }
    }

    public static Result openUrl(Context context, String url) {
        String target = url == null ? "" : url.trim();
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "https://" + target;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new Result(true, "Opening " + target);
        } catch (RuntimeException error) {
            return new Result(false, "No browser could open that link");
        }
    }

    /** Opens a supported system settings page. */
    public static Result openSettings(Context context, String page) {
        String action;
        String key = page == null ? "" : Strings.lower(page);
        if (key.contains("wifi")) {
            action = Settings.ACTION_WIFI_SETTINGS;
        } else if (key.contains("bluetooth")) {
            action = Settings.ACTION_BLUETOOTH_SETTINGS;
        } else if (key.contains("battery")) {
            action = Intent.ACTION_POWER_USAGE_SUMMARY;
        } else if (key.contains("location")) {
            action = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        } else if (key.contains("sound")) {
            action = Settings.ACTION_SOUND_SETTINGS;
        } else if (key.contains("display")) {
            action = Settings.ACTION_DISPLAY_SETTINGS;
        } else if (key.contains("app")) {
            action = Settings.ACTION_APPLICATION_SETTINGS;
        } else if (key.contains("notification")) {
            action = "android.settings.APP_NOTIFICATION_SETTINGS";
        } else {
            action = Settings.ACTION_SETTINGS;
        }
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new Result(true, "Opened settings");
        } catch (RuntimeException error) {
            return new Result(false, "This settings page is not available on your phone");
        }
    }

    /** Battery, storage and device facts for "how is my battery". */
    public static JsonObject systemInfo(Context context) {
        BatteryManager battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int level = battery == null ? -1 : battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        Intent status = context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temperature = status == null ? 0 : status.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0);
        boolean charging = status != null && status.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                == android.os.BatteryManager.BATTERY_STATUS_CHARGING;
        JsonObject info = new JsonObject();
        info.put("batteryPercent", Integer.valueOf(level));
        info.put("charging", Boolean.valueOf(charging));
        info.put("batteryTemperatureC", Double.valueOf(temperature / 10.0d));
        info.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        info.put("androidVersion", Build.VERSION.RELEASE);
        info.put("sdk", Integer.valueOf(Build.VERSION.SDK_INT));
        try {
            android.app.ActivityManager manager =
                    (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                android.app.ActivityManager.MemoryInfo memory = new android.app.ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memory);
                info.put("freeRamMb", Long.valueOf(memory.availMem / (1024L * 1024L)));
                info.put("lowRam", Boolean.valueOf(memory.lowMemory));
            }
        } catch (RuntimeException error) {
            Log.warn("AppControl", "RAM info unavailable: " + error.getMessage());
        }
        return info;
    }

    /** Places a call; the caller must have already asked the user to confirm. */
    public static Result call(Context context, String number) {
        String digits = number == null ? "" : number.replaceAll("[^+0-9]", "");
        if (digits.length() < 5) {
            return new Result(false, "That phone number doesn't look right");
        }
        boolean granted = context.checkSelfPermission(Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
        try {
            Intent intent = new Intent(granted ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + digits));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new Result(true, granted ? "Calling " + digits : "Opened the dialer for " + digits);
        } catch (RuntimeException error) {
            return new Result(false, "Could not open the dialer");
        }
    }

    /** Sends an SMS; requires the SEND_SMS permission and explicit confirmation. */
    public static Result sendSms(Context context, String number, String body) {
        String digits = number == null ? "" : number.replaceAll("[^+0-9]", "");
        if (digits.length() < 5) {
            return new Result(false, "That phone number doesn't look right");
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + digits));
            intent.putExtra("sms_body", body == null ? "" : body);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new Result(true, "SMS permission not granted - opened Messages so you can send it yourself");
        }
        try {
            SmsManager manager = SmsManager.getDefault();
            manager.sendTextMessage(digits, null, body == null ? "" : body, null, null);
            return new Result(true, "Message sent to " + digits);
        } catch (RuntimeException error) {
            return new Result(false, "The message could not be sent: " + error.getMessage());
        }
    }

    /** Opens WhatsApp with a pre-filled message; the user still taps send. */
    public static Result whatsapp(Context context, String number, String body) {
        String digits = number == null ? "" : number.replaceAll("[^0-9]", "");
        try {
            if (!digits.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/" + digits + "?text=" + Uri.encode(body == null ? "" : body)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.setPackage("com.whatsapp");
                intent.putExtra(Intent.EXTRA_TEXT, body == null ? "" : body);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            return new Result(true, "Opened WhatsApp - review the message and press send");
        } catch (RuntimeException error) {
            return new Result(false, "WhatsApp is not installed on this phone");
        }
    }

    public static Result share(Context context, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        Intent chooser = Intent.createChooser(intent, "Share with");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
        return new Result(true, "Choose an app to share with");
    }

    /** Installed, launchable apps (used for app-name suggestions). */
    public static JsonArray installedApps(Context context) {
        JsonArray array = new JsonArray();
        PackageManager manager = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : manager.queryIntentActivities(launcher, 0)) {
            ApplicationInfo applicationInfo = info.activityInfo.applicationInfo;
            if (applicationInfo == null) {
                continue;
            }
            array.add(new JsonObject()
                    .put("label", String.valueOf(info.loadLabel(manager)))
                    .put("package", info.activityInfo.packageName));
        }
        return array;
    }

    /** Maps an app name to a package for diagnostics. */
    public static String packageOf(Context context, String appName) {
        String needle = Strings.lower(appName);
        for (int i = 0; i < installedApps(context).size(); i++) {
            JsonObject app = installedApps(context).getObject(i);
            if (app != null && Strings.lower(app.getString("label", "")).contains(needle)) {
                return app.getString("package");
            }
        }
        return null;
    }

    public static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    /** Localised label used in confirmations. */
    public static String describe(Context context) {
        return String.format(Locale.US, "%s (%s)", android.os.Build.MODEL, android.os.Build.VERSION.RELEASE);
    }
}
