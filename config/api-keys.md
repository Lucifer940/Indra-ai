# Where to get each AI provider key

INDRA AI never ships with keys. Add them either:

1. **Per user (recommended, no server cost)** – Settings → AI Providers in the
   Android or Windows app. Keys are stored encrypted (Android Keystore / Windows
   DPAPI-like file protection) and can be reset at any time. Multiple keys per
   provider are supported; INDRA rotates automatically when one is rate limited.
2. **Server-wide fallback** – environment variables on the backend
   (`config/backend.env.example`). Users then get AI out of the box.

| Provider | Get a key from | Env variable | Notes |
|----------|----------------|--------------|-------|
| OpenAI | platform.openai.com → API keys | `OPENAI_API_KEY` | `gpt-4o-mini` is cheap and fast |
| DeepSeek | platform.deepseek.com → API keys | `DEEPSEEK_API_KEY` | very low cost, good reasoning |
| Google Gemini | aistudio.google.com → Get API key | `GOOGLE_AI_API_KEY` | `gemini-2.0-flash` has a free tier |
| Anthropic | console.anthropic.com → API keys | `ANTHROPIC_API_KEY` | Claude 3.5 models |
| OpenRouter | openrouter.ai → Keys | `OPENROUTER_API_KEY` | one key for many models |
| Groq | console.groq.com → API keys | `GROQ_API_KEY` | extremely low latency |
| GitHub Models | github.com/settings/tokens (fine-grained, `models:read`) | `GITHUB_MODELS_TOKEN` | useful if you already have GitHub |
| Ollama (local) | none – run `ollama serve` | – | fully offline on your PC |
| LM Studio (local) | none – start the local server | – | fully offline on your PC |

## Optional extras

| Feature | Key | Env variable | Without it |
|---------|-----|--------------|-----------|
| Weather | none | – | Open-Meteo works out of the box |
| Web/news search | Tavily | `TAVILY_API_KEY` | falls back to Wikipedia (no live news) |
| Google sign-in | Google Cloud OAuth client | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | button hidden / 503 with a clear message |
| GitHub sign-in | GitHub OAuth app | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | button hidden / 503 with a clear message |

## OAuth redirect URIs

Register these exact values so sign-in succeeds:

* Android: `indraai://oauth2redirect`
* Windows: `http://127.0.0.1:8421/oauth2redirect` (loopback listener in the app)
* Backend (if you proxy the callback): `https://your-server/api/v1/auth/oauth/<provider>/exchange`
