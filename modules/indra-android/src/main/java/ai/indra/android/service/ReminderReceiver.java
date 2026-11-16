package ai.indra.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ai.indra.android.IndraApp;
import ai.indra.core.model.Recurrence;
import ai.indra.core.model.TaskItem;
import ai.indra.core.tasks.RecurrenceEngine;
import ai.indra.core.util.Log;

/** Fires when a reminder is due: notifies, then rolls recurring reminders forward. */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String taskId = intent.getStringExtra("taskId");
        String title = intent.getStringExtra("title");
        String timeZone = intent.getStringExtra("timeZone");
        String recurrence = intent.getStringExtra("recurrence");
        if (taskId == null) {
            return;
        }
        IndraApp app = IndraApp.from(context);
        TaskItem task = null;
        for (TaskItem candidate : app.store().tasks(true)) {
            if (taskId.equals(candidate.getId())) {
                task = candidate;
                break;
            }
        }
        String displayTitle = task != null ? task.getTitle() : (title == null ? "Reminder" : title);
        NotificationHelper.showReminder(context, displayTitle,
                "INDRA AI reminder - tap to open", taskId.hashCode());

        if (task == null) {
            return;
        }
        Recurrence rule = Recurrence.fromId(recurrence == null ? task.getRecurrence().getId() : recurrence);
        if (rule.repeats()) {
            long next = RecurrenceEngine.nextOccurrence(task.getRemindAt(), rule,
                    timeZone == null ? java.util.TimeZone.getDefault().getID() : timeZone);
            if (next > 0L) {
                task.setRemindAt(next);
                task.setNotifiedAt(System.currentTimeMillis());
                task.setUpdatedAt(System.currentTimeMillis());
                app.store().saveTask(task);
                ReminderScheduler.schedule(context, task, timeZone);
                Log.info("ReminderReceiver", "Repeating reminder rescheduled to " + next);
            }
        } else {
            task.setNotifiedAt(System.currentTimeMillis());
            app.store().saveTask(task);
        }
    }
}
