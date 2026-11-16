package ai.indra.backend.service;

import ai.indra.backend.http.ApiException;
import ai.indra.backend.store.Repository;
import ai.indra.core.json.JsonArray;
import ai.indra.core.json.JsonObject;
import ai.indra.core.model.Recurrence;
import ai.indra.core.model.TaskItem;
import ai.indra.core.model.User;
import ai.indra.core.security.Validation;
import ai.indra.core.tasks.RecurrenceEngine;
import ai.indra.core.util.Strings;
import ai.indra.core.util.Times;

import java.util.List;

/** Tasks, reminders and alarms. */
public final class TaskService {

    private final Repository repository;

    public TaskService(Repository repository) {
        this.repository = repository;
    }

    public JsonObject list(User user, boolean includeCompleted) {
        List<TaskItem> tasks = repository.listTasks(user.getId(), includeCompleted);
        JsonArray array = new JsonArray();
        long now = System.currentTimeMillis();
        for (TaskItem task : tasks) {
            JsonObject json = task.toJson();
            long when = task.getRemindAt() > 0L ? task.getRemindAt() : task.getDueAt();
            json.put("whenIso", Times.toIso(when));
            json.put("overdue", Boolean.valueOf(!task.isCompleted() && when > 0L && when < now));
            array.add(json);
        }
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("tasks", array);
        response.put("count", Integer.valueOf(tasks.size()));
        return response;
    }

    public JsonObject create(User user, JsonObject body, String timeZone) {
        String title = Validation.requireText("title", body.getString("title"), 1, 200);
        TaskItem task = TaskItem.create(user.getId(), title);
        task.setNotes(Validation.optionalText("notes", body.getString("notes"), 2000));
        task.setDueAt(body.getLong("dueAt", 0L));
        task.setRemindAt(body.getLong("remindAt", 0L));
        task.setRecurrence(Recurrence.fromId(body.getString("recurrence", "none")));
        task.setPriority(body.getInt("priority", 2));
        task.setDeviceId(body.getString("deviceId"));
        task.setConversationId(body.getString("conversationId"));
        if (task.getRemindAt() > 0L && task.getRemindAt() < System.currentTimeMillis()) {
            throw ApiException.badRequest("That reminder time is in the past");
        }
        repository.saveTask(task);
        repository.audit(user.getId(), "task.create", title, null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("task", task.toJson());
        if (task.isReminder()) {
            response.put("message", RecurrenceEngine.acknowledgement(title, task.getRemindAt(),
                    task.getRecurrence(), timeZone));
        } else {
            response.put("message", "Task added: " + title);
        }
        return response;
    }

    public JsonObject update(User user, String id, JsonObject body) {
        TaskItem task = requireTask(user, id);
        if (body.has("title")) {
            task.setTitle(Validation.requireText("title", body.getString("title"), 1, 200));
        }
        if (body.has("notes")) {
            task.setNotes(Validation.optionalText("notes", body.getString("notes"), 2000));
        }
        if (body.has("dueAt")) {
            task.setDueAt(body.getLong("dueAt", task.getDueAt()));
        }
        if (body.has("remindAt")) {
            task.setRemindAt(body.getLong("remindAt", task.getRemindAt()));
        }
        if (body.has("recurrence")) {
            task.setRecurrence(Recurrence.fromId(body.getString("recurrence")));
        }
        if (body.has("priority")) {
            task.setPriority(body.getInt("priority", task.getPriority()));
        }
        if (body.has("completed")) {
            boolean completed = body.getBoolean("completed", task.isCompleted());
            task.setCompleted(completed);
            task.setCompletedAt(completed ? System.currentTimeMillis() : 0L);
        }
        repository.saveTask(task);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("task", task.toJson());
        return response;
    }

    /**
     * Marks a task done. Repeating reminders roll forward to their next
     * occurrence instead of completing forever.
     */
    public JsonObject complete(User user, String id, String timeZone) {
        TaskItem task = requireTask(user, id);
        if (task.getRecurrence().repeats() && task.getRemindAt() > 0L) {
            long next = RecurrenceEngine.nextOccurrence(task.getRemindAt(), task.getRecurrence(), timeZone);
            task.setRemindAt(next);
            task.setNotifiedAt(0L);
            repository.saveTask(task);
            JsonObject response = new JsonObject();
            response.put("ok", Boolean.TRUE);
            response.put("task", task.toJson());
            response.put("message", "Done. Next reminder: " + Times.toDisplay(next, timeZone));
            return response;
        }
        task.setCompleted(true);
        task.setCompletedAt(System.currentTimeMillis());
        repository.saveTask(task);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("task", task.toJson());
        response.put("message", "Task completed");
        return response;
    }

    public JsonObject delete(User user, String id) {
        TaskItem task = repository.findTask(user.getId(), id);
        if (task == null) {
            throw ApiException.notFound("Task not found");
        }
        repository.deleteTask(user.getId(), id);
        repository.audit(user.getId(), "task.delete", task.getTitle(), null);
        JsonObject response = new JsonObject();
        response.put("ok", Boolean.TRUE);
        response.put("message", "Task deleted");
        return response;
    }

    /** Tasks due in the next {@code windowMillis} for push notifications. */
    public List<TaskItem> dueSoon(User user, long windowMillis) {
        long limit = System.currentTimeMillis() + Math.max(0L, windowMillis);
        List<TaskItem> due = new java.util.ArrayList<TaskItem>();
        for (TaskItem task : repository.listTasks(user.getId(), false)) {
            long when = task.getRemindAt() > 0L ? task.getRemindAt() : task.getDueAt();
            if (when > 0L && when <= limit && task.getNotifiedAt() == 0L) {
                due.add(task);
            }
        }
        return due;
    }

    public void markNotified(TaskItem task) {
        task.setNotifiedAt(System.currentTimeMillis());
        repository.saveTask(task);
    }

    private TaskItem requireTask(User user, String id) {
        TaskItem task = repository.findTask(user.getId(), id);
        if (task == null) {
            throw ApiException.notFound("Task not found");
        }
        return task;
    }

    /** Finds a task by fuzzy title, used by "mark <thing> done". */
    public TaskItem findByTitle(User user, String title) {
        if (Strings.isBlank(title)) {
            return null;
        }
        for (TaskItem task : repository.listTasks(user.getId(), false)) {
            if (Strings.lower(task.getTitle()).contains(Strings.lower(title))
                    || Strings.lower(title).contains(Strings.lower(task.getTitle()))) {
                return task;
            }
        }
        return null;
    }
}
