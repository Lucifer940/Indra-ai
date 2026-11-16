package ai.indra.core.ai;

import ai.indra.core.memory.MemoryEngine;
import ai.indra.core.model.BrainProfile;
import ai.indra.core.model.ChatMessage;
import ai.indra.core.model.Language;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.model.Persona;
import ai.indra.core.model.TaskItem;
import ai.indra.core.model.UserSettings;
import ai.indra.core.tasks.RecurrenceEngine;
import ai.indra.core.util.Times;

import java.util.List;

/** Assembles a provider request from conversation state plus recalled context. */
public final class PromptBuilder {

    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final int MAX_HISTORY_CHARS = 6000;

    private PromptBuilder() {
    }

    public static ChatRequest build(UserSettings settings, String userName, List<ChatMessage> history,
                                    List<MemoryItem> memories, List<TaskItem> openTasks, String userMessage,
                                    String timeZone, boolean voiceMode) {
        UserSettings safeSettings = settings == null ? UserSettings.defaults("anonymous") : settings;
        Language language = safeSettings.getLanguage();
        Persona persona = safeSettings.getPersona();
        BrainProfile brain = safeSettings.getBrain();

        long now = System.currentTimeMillis();
        PersonalityEngine.Context context = new PersonalityEngine.Context()
                .setUserName(userName)
                .setTimeZone(timeZone)
                .setLocalTime(Times.toDisplay(now, timeZone))
                .setMemoriesBlock(MemoryEngine.formatForPrompt(memories, 25))
                .setTasksBlock(formatTasks(openTasks, timeZone))
                .setVoiceMode(voiceMode);

        ChatRequest request = new ChatRequest();
        request.setProvider(safeSettings.getProvider());
        request.setModel(safeSettings.getModel());
        request.setLanguageCode(language.getCode());
        request.setPersonaId(persona.getId());
        request.setBrainId(brain.getId());
        request.setStream(safeSettings.isStreamingEnabled());
        request.system(PersonalityEngine.systemPrompt(persona, brain, language, context));

        if (history != null) {
            int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
            int budget = MAX_HISTORY_CHARS;
            for (int i = history.size() - 1; i >= start; i--) {
                ChatMessage message = history.get(i);
                if (message.getRole() == ai.indra.core.model.MessageRole.SYSTEM) {
                    continue;
                }
                String content = message.getContent() == null ? "" : message.getContent();
                budget -= content.length();
                if (budget < 0) {
                    break;
                }
                request.add(AiMessage.fromRole(message.getRole(), content));
            }
        }
        request.user(userMessage == null ? "" : userMessage.trim());
        return request;
    }

    public static String formatTasks(List<TaskItem> tasks, String timeZone) {
        if (tasks == null || tasks.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (TaskItem task : tasks) {
            if (count++ >= 10) {
                break;
            }
            out.append("- ");
            if (task.isReminder()) {
                out.append("Reminder: ");
            }
            out.append(task.getTitle());
            if (task.getRemindAt() > 0L) {
                out.append(" at ").append(Times.toDisplay(task.getRemindAt(), timeZone));
            } else if (task.getDueAt() > 0L) {
                out.append(" due ").append(Times.toDisplay(task.getDueAt(), timeZone));
            }
            if (task.getRecurrence().repeats()) {
                out.append(" (").append(RecurrenceEngine.describe(task.getRecurrence())).append(")");
            }
            out.append('\n');
        }
        return out.toString();
    }
}
