package ai.indra.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ai.indra.android.IndraApp;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.TaskItem;
import ai.indra.core.util.Log;

/**
 * Restores reminder alarms after a reboot or an app update.
 *
 * <p>Tasks live in the on-device engine, so this receiver asks the engine for the
 * open reminders instead of a separate local table. Android starts the application
 * process before delivering the boot broadcast, which means the engine is already
 * booting; the request waits for it and then reschedules every future alarm.</p>
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context appContext = context.getApplicationContext();
        final PendingResult pending = goAsync();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                int restored = 0;
                try {
                    IndraApp app = IndraApp.from(appContext);
                    if (!app.session().isSignedIn()) {
                        Log.info("BootReceiver", "No session yet - reminders will be restored after sign-in");
                        return;
                    }
                    JsonObject response = app.api().tasks(false);
                    JsonArray tasks = response.getArrayOrEmpty("tasks");
                    String zone = java.util.TimeZone.getDefault().getID();
                    for (int i = 0; i < tasks.size(); i++) {
                        JsonObject json = tasks.getObject(i);
                        if (json == null) {
                            continue;
                        }
                        TaskItem task = TaskItem.fromJson(json);
                        long when = task.getRemindAt() > 0L ? task.getRemindAt() : task.getDueAt();
                        if (task.isCompleted() || when <= System.currentTimeMillis()) {
                            continue;
                        }
                        ReminderScheduler.schedule(appContext, task, zone);
                        restored++;
                    }
                    Log.info("BootReceiver", "Restored " + restored + " reminder alarms after boot");
                } catch (Exception error) {
                    Log.warn("BootReceiver", "Could not restore reminders: " + error.getMessage());
                } finally {
                    pending.finish();
                }
            }
        }, "indra-boot-restore");
        worker.setDaemon(true);
        worker.start();
    }
}
