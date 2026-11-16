package ai.indra.android.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ai.indra.core.model.TaskItem;
import ai.indra.core.tasks.RecurrenceEngine;
import ai.indra.core.util.Log;

/** Schedule and cancel exact reminder alarms. */
public final class ReminderScheduler {

    private ReminderScheduler() {
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    public static void schedule(Context context, TaskItem task, String timeZone) {
        long when = task.getRemindAt() > 0L ? task.getRemindAt() : task.getDueAt();
        if (when <= System.currentTimeMillis()) {
            return;
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        PendingIntent pending = pendingIntent(context, task, timeZone);
        boolean exact = canScheduleExact(context);
        try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            }
        } catch (SecurityException error) {
            Log.warn("ReminderScheduler", "Exact alarm not permitted, using inexact: " + error.getMessage());
            manager.set(AlarmManager.RTC_WAKEUP, when, pending);
        }
    }

    public static void cancel(Context context, String taskId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("ai.indra.android.REMINDER");
        intent.putExtra("taskId", taskId);
        PendingIntent pending = PendingIntent.getBroadcast(context, taskId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.cancel(pending);
    }

    public static void rescheduleAll(Context context) {
        ai.indra.android.IndraApp app = ai.indra.android.IndraApp.from(context);
        String zone = java.util.TimeZone.getDefault().getID();
        for (TaskItem task : app.store().tasks(false)) {
            if (task.isReminder()) {
                schedule(context, task, zone);
            }
        }
    }

    private static PendingIntent pendingIntent(Context context, TaskItem task, String timeZone) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("ai.indra.android.REMINDER");
        intent.putExtra("taskId", task.getId());
        intent.putExtra("title", task.getTitle());
        intent.putExtra("recurrence", task.getRecurrence().getId());
        intent.putExtra("remindAt", task.getRemindAt());
        intent.putExtra("timeZone", timeZone);
        return PendingIntent.getBroadcast(context, task.getId().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Next occurrence for recurring reminders (exposed for tests/instrumentation). */
    public static long nextOccurrence(TaskItem task, String timeZone) {
        return RecurrenceEngine.nextOccurrence(task.getRemindAt(), task.getRecurrence(), timeZone);
    }
}
