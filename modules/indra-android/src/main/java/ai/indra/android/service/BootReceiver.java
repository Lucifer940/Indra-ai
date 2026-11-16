package ai.indra.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ai.indra.android.IndraApp;
import ai.indra.core.util.Log;

/** Restores alarms after a reboot or an app update. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        IndraApp app = IndraApp.from(context);
        int restored = 0;
        for (ai.indra.core.model.TaskItem task : app.store().tasks(false)) {
            if (task.isReminder()) {
                ReminderScheduler.schedule(context, task, java.util.TimeZone.getDefault().getID());
                restored++;
            }
        }
        Log.info("BootReceiver", "Restored " + restored + " reminder alarms after boot");
    }
}
