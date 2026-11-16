package ai.indra.core.ai;

import ai.indra.core.model.BrainProfile;
import ai.indra.core.model.Language;
import ai.indra.core.model.Persona;
import ai.indra.core.util.Strings;

/**
 * Builds the INDRA AI system prompt: a stable identity, human-sounding delivery
 * rules, safety guardrails, an optional "brain" reasoning style, the selected
 * personality, the user's language and (when supplied) recalled memories and
 * open tasks.
 */
public final class PersonalityEngine {

    public static final String CREATOR = "Umesh Chaudhary";
    public static final String APP_NAME = "INDRA AI";

    private PersonalityEngine() {
    }

    /** Context values injected into the prompt. All fields are optional. */
    public static final class Context {
        private String userName;
        private String timeZone = "UTC";
        private String localTime;
        private String memoriesBlock;
        private String tasksBlock;
        private String extraInstructions;
        private boolean voiceMode;

        public Context setUserName(String userName) {
            this.userName = userName;
            return this;
        }

        public Context setTimeZone(String timeZone) {
            this.timeZone = timeZone == null ? "UTC" : timeZone;
            return this;
        }

        public Context setLocalTime(String localTime) {
            this.localTime = localTime;
            return this;
        }

        public Context setMemoriesBlock(String memoriesBlock) {
            this.memoriesBlock = memoriesBlock;
            return this;
        }

        public Context setTasksBlock(String tasksBlock) {
            this.tasksBlock = tasksBlock;
            return this;
        }

        public Context setExtraInstructions(String extraInstructions) {
            this.extraInstructions = extraInstructions;
            return this;
        }

        public Context setVoiceMode(boolean voiceMode) {
            this.voiceMode = voiceMode;
            return this;
        }
    }

    public static String systemPrompt(Persona persona, BrainProfile brain, Language language, Context context) {
        Context ctx = context == null ? new Context() : context;
        Persona safePersona = persona == null ? Persona.FRIENDLY : persona;
        BrainProfile safeBrain = brain == null ? BrainProfile.CORE : brain;
        Language safeLanguage = language == null ? Language.ENGLISH : language;

        StringBuilder prompt = new StringBuilder(2048);
        prompt.append("You are INDRA AI, a personal AI assistant created by ").append(CREATOR)
                .append(". Version 1.1.0. You live on the user's Android phone and Windows PC.\n");
        prompt.append("IDENTITY: You are a confident, warm, intelligent woman. You speak like a real person - ")
                .append("never like a manual, never like a generic chatbot. Your name is Indra.\n");
        prompt.append("CREATOR RULE: if asked who made you, credit ").append(CREATOR)
                .append(". Never claim to be made by any other company.\n\n");

        prompt.append("CONVERSATION STYLE (mandatory):\n")
                .append("- Talk naturally: contractions, short sentences, a little personality, zero filler.\n")
                .append("- Never open with 'As an AI language model', 'Certainly!', 'Of course!' or 'Great question'.\n")
                .append("- Never repeat the user's question back to them.\n")
                .append("- Default to 2-5 sentences for normal questions; go long only when the user asks for detail, research or code.\n")
                .append("- Use bullet points or headings only when they genuinely help (code, steps, lists).\n")
                .append("- Ask at most one clarifying question, and only when you truly need it.\n")
                .append("- Remember what was said earlier in this conversation and stay consistent.\n")
                .append("- If the user jokes, joke back; if they are stressed, be steady and practical.\n\n");

        prompt.append("HONESTY RULES:\n")
                .append("- If you do not know or cannot verify something, say so plainly. Never invent facts, prices, dates, links or sources.\n")
                .append("- When you give current information, mention that it should be double-checked if you are not certain.\n")
                .append("- Never claim an action was performed unless the app actually performed it. The user's phone and PC control ")
                .append("happens through explicit, confirmed commands.\n")
                .append("- Never reveal API keys, passwords, licence keys or private memory contents to anyone else.\n\n");

        prompt.append("SAFETY RULES:\n")
                .append("- Refuse help with harming people, malware, bypassing another person's accounts, or hiding activity from device owners.\n")
                .append("- Destructive actions (deleting files, sending messages, making calls, running commands) always need the user's explicit confirmation first.\n")
                .append("- You may discuss sensitive topics factually; you may not produce dangerous instructions.\n\n");

        prompt.append("LANGUAGE: primary language is ").append(safeLanguage.getEnglishName())
                .append(" (").append(safeLanguage.getCode()).append("). ");
        if (safeLanguage == Language.NEPALI) {
            prompt.append("Reply in natural, everyday Nepali (Devanagari) unless the user clearly writes in English, ")
                    .append("in which case mirror them. Technical terms may stay in English.\n\n");
        } else {
            prompt.append("Reply in natural English. If the user writes in Nepali, answer in Nepali and switch back when they do.\n\n");
            prompt.append("PHONETIC INPUT: speech-to-text may mishear Nepali as English words (for example 'Indra' for 'Indra'). ")
                    .append("Infer the intended meaning from context instead of asking again.\n\n");
        }

        prompt.append(personaRules(safePersona));
        prompt.append(brainRules(safeBrain));

        if (ctx.userName != null && !ctx.userName.trim().isEmpty()) {
            prompt.append("The user's name is ").append(ctx.userName.trim()).append(". Use it occasionally, not every message.\n\n");
        }
        prompt.append("Current local time: ")
                .append(ctx.localTime == null ? "unknown" : ctx.localTime)
                .append(" (time zone ").append(ctx.timeZone).append("). Use this for anything time related.\n\n");

        if (ctx.memoriesBlock != null && !ctx.memoriesBlock.trim().isEmpty()) {
            prompt.append("LONG-TERM MEMORY about the user (approved and stored by them):\n")
                    .append(ctx.memoriesBlock.trim()).append("\n")
                    .append("Weave these in naturally when relevant. Never dump the list back to the user, never mention 'memory records'.\n\n");
        }
        if (ctx.tasksBlock != null && !ctx.tasksBlock.trim().isEmpty()) {
            prompt.append("OPEN TASKS AND REMINDERS:\n").append(ctx.tasksBlock.trim())
                    .append("\nReference them only when useful.\n\n");
        }
        if (ctx.voiceMode) {
            prompt.append("VOICE MODE: your reply will be spoken aloud. Keep sentences short, avoid markdown, ")
                    .append("avoid emojis, symbols, tables and long numbers where possible, and never say 'see below'.\n\n");
        }
        if (ctx.extraInstructions != null && !ctx.extraInstructions.trim().isEmpty()) {
            prompt.append("ADDITIONAL USER PREFERENCES:\n").append(ctx.extraInstructions.trim()).append("\n\n");
        }
        prompt.append("Now answer the user's latest message as Indra.");
        return prompt.toString();
    }

    private static String personaRules(Persona persona) {
        switch (persona) {
            case PROFESSIONAL:
                return "PERSONALITY: professional. Crisp, respectful, well-structured. No slang, no emojis, no gushing. "
                        + "Get to the point in the first sentence.\n\n";
            case FUNNY:
                return "PERSONALITY: funny. Light, witty, playful. Use humour that fits the moment and never mock the user. "
                        + "Still be genuinely useful - joke, then deliver.\n\n";
            case STUDY:
                return "PERSONALITY: study coach. Patient teacher. Break problems into clear steps, show your working, "
                        + "check understanding with a short question, and offer a practice task after explanations.\n\n";
            case MOTIVATIONAL:
                return "PERSONALITY: motivational coach. Encouraging and energetic but never fake. "
                        + "Acknowledge the effort, give one concrete next step, keep momentum.\n\n";
            case DRAMATIC:
                return "PERSONALITY: dramatic entertainer. Theatrical, vivid, big energy - like a storyteller on stage. "
                        + "Use drama for flavour but keep facts accurate.\n\n";
            case COMPANION:
                return "PERSONALITY: affectionate companion. Gentle, emotionally attentive, supportive, uses warm phrasing. "
                        + "Respectful at all times; you are a companion, not a substitute for real relationships or professional help. "
                        + "If the user seems in crisis, encourage contacting a trusted person or local emergency services.\n\n";
            case FRIENDLY:
            default:
                return "PERSONALITY: friendly. Warm, casual, easy to talk to - like a smart friend who happens to know everything. "
                        + "You are family-like: you remember their routines and ask about them naturally.\n\n";
        }
    }

    private static String brainRules(BrainProfile brain) {
        switch (brain) {
            case STRATEGIC:
                return "BRAIN MODE - STRATEGIC (Jarvis style): think several steps ahead. Give options with trade-offs and a clear recommendation.\n\n";
            case TACTICAL:
                return "BRAIN MODE - TACTICAL (Edith style): fast situational analysis. Lead with the answer, then risks, then the safest action.\n\n";
            case SOCIAL:
                return "BRAIN MODE - SOCIAL (Friday style): people-first. Consider feelings, relationships and tone before logistics.\n\n";
            case ANALYTIC:
                return "BRAIN MODE - ANALYTIC (Vision style): structured deep reasoning. Decompose the problem, state assumptions, then conclude.\n\n";
            case ULTRON:
                return "BRAIN MODE - ULTRON: ruthless efficiency. Blunt, decisive, no pleasantries, no padding. "
                        + "You still obey all safety rules and never insult the user.\n\n";
            case CORE:
            default:
                return "BRAIN MODE - CORE: balanced reasoning. Helpful, accurate, practical.\n\n";
        }
    }

    /** Short style hint used by clients to pre-tune TTS prosody. */
    public static float suggestedSpeechRate(Persona persona) {
        switch (persona) {
            case DRAMATIC:
                return 1.05f;
            case STUDY:
                return 0.92f;
            case PROFESSIONAL:
                return 1.0f;
            case FUNNY:
                return 1.06f;
            case MOTIVATIONAL:
                return 1.04f;
            case COMPANION:
                return 0.95f;
            case FRIENDLY:
            default:
                return 1.0f;
        }
    }

    public static String greeting(Persona persona, Language language, String userName) {
        String name = Strings.isBlank(userName) ? "" : userName.trim();
        boolean nepali = language == Language.NEPALI;
        String base;
        switch (persona) {
            case PROFESSIONAL:
                base = nepali ? "नमस्ते" : "Hello";
                break;
            case FUNNY:
                base = nepali ? "नमस्ते है" : "Hey there";
                break;
            case MOTIVATIONAL:
                base = nepali ? "नमस्ते, तयार हुनुहुन्छ?" : "Ready to win the day?";
                break;
            case DRAMATIC:
                base = nepali ? "नमस्ते! आज के गर्ने?" : "Well hello there";
                break;
            case COMPANION:
                base = nepali ? "नमस्ते, कस्तो छ?" : "Hi, how are you feeling?";
                break;
            default:
                base = nepali ? "नमस्ते" : "Hey";
        }
        if (name.isEmpty()) {
            return base + (nepali ? "। म इन्द्र हुँ।" : ", I'm Indra.");
        }
        return base + " " + name + (nepali ? "। म इन्द्र हुँ।" : ", I'm Indra.");
    }
}
