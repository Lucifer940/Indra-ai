package ai.indra.core.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue of supported AI providers. Every provider is called server-side or
 * through a user-supplied key stored in the encrypted vault; keys never ship
 * inside the APK or the Windows installer.
 */
public final class ProviderCatalog {

    /** Wire format used by a provider. */
    public enum Style {
        OPENAI_COMPATIBLE,
        GEMINI,
        ANTHROPIC
    }

    /** Immutable provider description. */
    public static final class Provider {
        private final String id;
        private final String label;
        private final String baseUrl;
        private final Style style;
        private final String envVar;
        private final List<String> models;
        private final boolean requiresKey;
        private final boolean supportsStreaming;
        private final String notes;

        Provider(String id, String label, String baseUrl, Style style, String envVar,
                 List<String> models, boolean requiresKey, boolean supportsStreaming, String notes) {
            this.id = id;
            this.label = label;
            this.baseUrl = baseUrl;
            this.style = style;
            this.envVar = envVar;
            this.models = models;
            this.requiresKey = requiresKey;
            this.supportsStreaming = supportsStreaming;
            this.notes = notes;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public Style getStyle() {
            return style;
        }

        public String getEnvVar() {
            return envVar;
        }

        public List<String> getModels() {
            return models;
        }

        public String getDefaultModel() {
            return models.isEmpty() ? "" : models.get(0);
        }

        public boolean isRequiresKey() {
            return requiresKey;
        }

        public boolean isSupportsStreaming() {
            return supportsStreaming;
        }

        public String getNotes() {
            return notes;
        }
    }

    private static final Map<String, Provider> PROVIDERS = new LinkedHashMap<String, Provider>();

    static {
        register(new Provider("openai", "OpenAI", "https://api.openai.com/v1", Style.OPENAI_COMPATIBLE,
                "OPENAI_API_KEY", Arrays.asList("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"),
                true, true, "Official OpenAI Chat Completions API"));
        register(new Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", Style.OPENAI_COMPATIBLE,
                "DEEPSEEK_API_KEY", Arrays.asList("deepseek-chat", "deepseek-reasoner"),
                true, true, "Very cost effective reasoning models"));
        register(new Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", Style.OPENAI_COMPATIBLE,
                "OPENROUTER_API_KEY", Arrays.asList("openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet", "google/gemini-flash-1.5"),
                true, true, "One key, many models"));
        register(new Provider("github", "GitHub Models", "https://models.github.ai/inference", Style.OPENAI_COMPATIBLE,
                "GITHUB_MODELS_TOKEN", Arrays.asList("openai/gpt-4o-mini", "meta/Llama-3.3-70B-Instruct"),
                true, true, "Uses a GitHub personal access token with models:read"));
        register(new Provider("google", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", Style.GEMINI,
                "GOOGLE_AI_API_KEY", Arrays.asList("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro"),
                true, true, "Google AI Studio API key"));
        register(new Provider("anthropic", "Anthropic Claude", "https://api.anthropic.com/v1", Style.ANTHROPIC,
                "ANTHROPIC_API_KEY", Arrays.asList("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest"),
                true, true, "Messages API"));
        register(new Provider("groq", "Groq", "https://api.groq.com/openai/v1", Style.OPENAI_COMPATIBLE,
                "GROQ_API_KEY", Arrays.asList("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
                true, true, "Very low latency"));
        register(new Provider("ollama", "Ollama (local PC)", "http://127.0.0.1:11434/v1", Style.OPENAI_COMPATIBLE,
                "", Arrays.asList("llama3.1", "qwen2.5", "mistral"), false, true, "Runs offline on your own computer"));
        register(new Provider("lmstudio", "LM Studio (local PC)", "http://127.0.0.1:1234/v1", Style.OPENAI_COMPATIBLE,
                "", Arrays.asList("local-model"), false, true, "Runs offline on your own computer"));
        register(new Provider("offline", "Offline Brain", "", Style.OPENAI_COMPATIBLE,
                "", new ArrayList<String>(), false, false, "Deterministic local answers when no AI key is configured"));
    }

    private static void register(Provider provider) {
        PROVIDERS.put(provider.getId(), provider);
    }

    private ProviderCatalog() {
    }

    public static Provider get(String id) {
        if (id == null) {
            return PROVIDERS.get("offline");
        }
        Provider provider = PROVIDERS.get(id.trim().toLowerCase(java.util.Locale.ROOT));
        return provider == null ? PROVIDERS.get("offline") : provider;
    }

    public static boolean exists(String id) {
        return id != null && PROVIDERS.containsKey(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static List<Provider> all() {
        return new ArrayList<Provider>(PROVIDERS.values());
    }

    public static List<String> ids() {
        return new ArrayList<String>(PROVIDERS.keySet());
    }

    /** Providers that a user can add a personal key for from Settings. */
    public static List<Provider> keyProviders() {
        List<Provider> result = new ArrayList<Provider>();
        for (Provider provider : PROVIDERS.values()) {
            if (provider.isRequiresKey()) {
                result.add(provider);
            }
        }
        return result;
    }
}
