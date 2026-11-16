package ai.indra.android.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ai.indra.android.MainActivity;
import ai.indra.android.R;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/** Notification channels and helpers (reminders, voice service, alerts). */
public final class NotificationHelper {

    public static final String CHANNEL_VOICE = "indra_voice";
    public static final String CHANNEL_REMINDERS = "indra_reminders";
    public static final String CHANNEL_SERVICE = "indra_service";

    private NotificationHelper() {
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel voice = new NotificationChannel(CHANNEL_VOICE,
                context.getString(R.string.notification_channel_voice), NotificationManager.IMPORTANCE_LOW);
        voice.setDescription("Shown while INDRA is listening hands-free");
        manager.createNotificationChannel(voice);

        NotificationChannel reminders = new NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.notification_channel_reminders), NotificationManager.IMPORTANCE_HIGH);
        reminders.setDescription("Reminders, alarms and tasks you asked for");
        manager.createNotificationChannel(reminders);

        NotificationChannel service = new NotificationChannel(CHANNEL_SERVICE,
                context.getString(R.string.notification_channel_service), NotificationManager.IMPORTANCE_MIN);
        service.setDescription("Background sync and maintenance");
        manager.createNotificationChannel(service);
    }

    private static PendingIntent openApp(Context context, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Notification used by the visible hands-free service. */
    public static Notification voiceServiceNotification(Context context, boolean listening) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_VOICE)
                .setSmallIcon(R.drawable.ic_indra_logo)
                .setContentTitle(listening ? "Indra is listening" : "Indra is ready")
                .setContentText(listening ? "Say \"Indra\" followed by your request"
                        : "Tap to open INDRA AI")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openApp(context, 10));
        return builder.build();
    }

    public static void showReminder(Context context, String title, String body, int notificationId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_indra_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp(context, notificationId));
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException error) {
            // POST_NOTIFICATIONS not granted: the reminder still exists in the app.
        }
    }

    public static void showSimple(Context context, String title, String body) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SERVICE)
                .setSmallIcon(R.drawable.ic_indra_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(openApp(context, 11))
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(title.hashCode(), builder.build());
        } catch (SecurityException error) {
            // notifications disabled by the user
        }
    }
}
