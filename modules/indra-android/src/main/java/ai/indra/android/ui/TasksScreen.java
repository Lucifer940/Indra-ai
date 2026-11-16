package ai.indra.android.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import ai.indra.android.MainActivity;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.TaskItem;
import ai.indra.core.tasks.NaturalTimeParser;
import ai.indra.core.tasks.RecurrenceEngine;
import ai.indra.core.util.Times;

import java.util.List;

/** Tasks, reminders and alarms with natural-language time entry. */
public final class TasksScreen extends Screen {

    private final ScrollView root;
    private final LinearLayout list;

    public TasksScreen(MainActivity host) {
        super(host);
        Context context = host;
        LinearLayout column = UiKit.screen(context, 16);
        column.setBackgroundColor(UiKit.BG);
        column.addView(UiKit.title(context, "Tasks & reminders"));
        column.addView(UiKit.subtitle(context, "Type a time naturally: \"tomorrow at 9 am\", \"in 2 hours\", \"भोलि बिहान ९ बजे\"."));

        Button add = UiKit.button(context, "Add task or reminder", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddDialog();
            }
        });
        column.addView(add);

        list = UiKit.vertical(context);
        column.addView(list);
        root = UiKit.scroll(context, column);
    }

    @Override
    public View build() {
        reload();
        return root;
    }

    @Override
    public String label() {
        return "Tasks";
    }

    public void reload() {
        list.removeAllViews();
        List<TaskItem> tasks = host.store().tasks(false);
        if (tasks.isEmpty()) {
            LinearLayout card = UiKit.card(host);
            card.addView(UiKit.text(host, "No open tasks", 15f, UiKit.TEXT, true));
            card.addView(UiKit.subtitle(host, "Try: \"Remind me tomorrow at 8 am to go for a run\"."));
            list.addView(card);
            return;
        }
        Context context = host;
        for (final TaskItem task : tasks) {
            LinearLayout card = UiKit.card(context);
            LinearLayout row = UiKit.horizontal(context);
            final android.widget.CheckBox done = new android.widget.CheckBox(context);
            done.setChecked(task.isCompleted());
            done.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.completeTask(task.getId());
                }
            });
            row.addView(done);
            LinearLayout texts = UiKit.vertical(context);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            texts.addView(UiKit.text(context, task.getTitle(), 15f, UiKit.TEXT, false));
            long when = task.getRemindAt() > 0L ? task.getRemindAt() : task.getDueAt();
            String whenText = when > 0L
                    ? Times.toDisplay(when, java.util.TimeZone.getDefault().getID()) : "no time set";
            if (task.getRecurrence().repeats()) {
                whenText = whenText + " · " + RecurrenceEngine.describe(task.getRecurrence());
            }
            texts.addView(UiKit.text(context, whenText, 12f,
                    when > 0L && when < System.currentTimeMillis() ? UiKit.WARNING : UiKit.TEXT_DIM, false));
            row.addView(texts);
            Button delete = UiKit.smallButton(context, "Delete");
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(host)
                            .setTitle("Delete task?")
                            .setMessage(task.getTitle())
                            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    host.deleteTask(task.getId());
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
            row.addView(delete);
            card.addView(row);
            list.addView(card);
        }
    }

    private void showAddDialog() {
        Context context = host;
        LinearLayout form = UiKit.vertical(context);
        form.setPadding(UiKit.dp(context, 16), UiKit.dp(context, 8), UiKit.dp(context, 16), 0);
        final EditText title = UiKit.input(context, "What should I remind you about?",
                android.text.InputType.TYPE_CLASS_TEXT);
        form.addView(title);
        final EditText when = UiKit.input(context, "When? e.g. tomorrow at 9 am, in 30 minutes",
                android.text.InputType.TYPE_CLASS_TEXT);
        form.addView(when);
        final EditText repeat = UiKit.singleLine(context, "Repeat: none, daily, weekdays, weekly, monthly, yearly");
        form.addView(repeat);
        new AlertDialog.Builder(context)
                .setTitle("New task")
                .setView(form)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = title.getText().toString().trim();
                        String whenText = when.getText().toString().trim();
                        String recurrence = repeat.getText().toString().trim();
                        if (text.isEmpty()) {
                            return;
                        }
                        String zone = java.util.TimeZone.getDefault().getID();
                        NaturalTimeParser.Result parsed = NaturalTimeParser.parse(whenText,
                                System.currentTimeMillis(), zone);
                        JsonObject body = new JsonObject();
                        body.put("title", text);
                        body.put("timeZone", zone);
                        body.put("recurrence", recurrence.isEmpty() ? "none" : recurrence.toLowerCase(java.util.Locale.ROOT));
                        if (parsed.isFound()) {
                            body.put("remindAt", Long.valueOf(parsed.getMillis()));
                        } else if (!whenText.isEmpty()) {
                            host.toast("I couldn't understand that time - try \"tomorrow at 9 am\"");
                            return;
                        }
                        host.createTask(body);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
