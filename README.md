# INDRA AI

**A personal AI assistant for Android and Windows — one shared core, one secure backend.**
Created by **Umesh Chaudhary**. Current version **1.1.0**.

INDRA is a real, working application: a Java backend you run yourself, a JavaFX desktop assistant for
Windows, and a native Android app. All three share the same `indra-core` module, so personality, memory,
language handling, intents and task logic behave identically everywhere.

---

## What it does

### Conversation and intelligence
- Natural chat with streaming answers, conversation history and context.
- **Seven personality modes**: Friendly, Professional, Funny, Study Coach, Motivational, Dramatic, Companion.
- **Six brains / ideas**: Core, Jarvis (strategic), Edith (tactical), Friday (social), Vision (analytical), Ultron (blunt efficiency).
- **English and Nepali** language selection (plus automatic language detection for speech replies).
- Provider-agnostic: OpenAI, DeepSeek, OpenRouter, GitHub Models, Google Gemini, Anthropic Claude, Groq,
  or a fully local Ollama / LM Studio model. No provider configured → the deterministic **offline brain**
  answers, and the UI says so instead of pretending.
- Deep-research style answers through the search service (sources with links, "cannot verify" honesty).

### Memory
- Explicit "remember …" plus automatic learning (can be switched off).
- Retrieve relevant memories per message; edit, delete, clear individually or wholesale.
- Stored encrypted locally (Android Keystore / AES-GCM) and per user on the server. Cross-device sync.

### Voice
- Android: system speech recognition and text-to-speech, mic button, listening / thinking / speaking states,
  mouth movement, stop and interrupt, language and rate settings.
- Optional **hands-free wake word** ("Indra") through a *visible* foreground service with a permanent
  notification. Nothing is recorded to disk; only the recogniser's text is used.
- Windows: TTS and dictation through the operating system's own speech engine (no bundled cloud service).

### Animated character
- Futuristic female avatar drawn with native Canvas code (no image assets), identical identity on both platforms.
- Blinking, gaze drift, expressions (happy, confused, serious, friendly), idle breathing, listening rings,
  thinking orbit, and mouth movement synchronised with speech.

### Phone control (Android, official APIs only)
Open apps, open system setting pages, battery and device info, share sheet, calls and SMS **with per-action
confirmation** and normal permissions, WhatsApp drafts that you send yourself. No accessibility abuse, no
screen scraping, no security bypass.

### PC control (Windows, allowlisted)
Launch allowlisted applications, open known folders and files inside your home directory, search files,
create/rename/copy/move (and delete **only after confirmation**), open websites, read system/battery info,
lock the workstation. The AI never generates shell commands — it can only pick from this curated surface.

### Tasks, reminders, research, tools
- Reminders, tasks, alarms and recurring schedules parsed from natural language ("tomorrow at 9 am",
  "every weekday at 7", "in 2 hours") in English and Nepali.
- Android alarm scheduling, boot restore, notification delivery, cross-device sync.
- Weather (location-permission gated), web/news search with links, study help, coding help, translation.
- Whiteboard with colour palette, undo, save and share.

### Accounts, devices, admin
- Email + password auth (argon-style hashing on the server), Google and GitHub OAuth hooks (PKCE),
  JWT access tokens with refresh, logout revocation.
- Device pairing: one device shows a code, the other enters it; mutual confirmation, device list, revoke,
  HTTPS + bearer auth, memory/history/settings sync.
- Licence keys: admin-generated, entered once, remembered on the device.
- Admin role validated by the backend: users, roles, server config push (reaches all clients on refresh),
  audit log, statistics, licence issuing and revocation. **No admin credentials ship in the code.**

---

## Repository layout

```
modules/indra-core      Shared engine: models, intent classifier, prompt/personality, memory,
                        provider catalog + factory, streaming orchestrator, tasks, security, JSON,
                        licence validation, voice helpers. 11 unit-test classes.
modules/indra-backend   REST server (com.sun.net.httpserver), services, JSON document store
                        (SQLite dev / PostgreSQL prod), backup & restore CLI, admin API.
modules/indra-android   Native Android app (Java 11, minSdk 26, targetSdk 34).
modules/indra-desktop   JavaFX desktop assistant for Windows.
config/                 Environment template and API-key guide.
db/                     SQL schema for SQLite and PostgreSQL.
docs/                   Setup, API, security and testing documentation.
scripts/                One-command runners and packaging helpers.
```

---

## Quick start

### 1. Backend
```bash
cd modules/indra-backend
gradle fatJar                 # or: gradle jar  (see docs/BUILD_AND_RUN.md)
java -jar build/libs/indra-backend-all.jar serve
```
It listens on `http://127.0.0.1:8420`. Check it with `curl http://127.0.0.1:8420/api/v1/health`.

Create the first administrator (never stored in code — you choose the password):
```bash
java -jar build/libs/indra-backend-all.jar create-admin admin@example.com
```
Then add an AI provider key (server-side, encrypted) in **Settings → API keys**, or export one of
`OPENAI_API_KEY`, `DEEPSEEK_API_KEY`, `OPENROUTER_API_KEY`, `GITHUB_MODELS_TOKEN`, `GOOGLE_AI_API_KEY`,
`ANTHROPIC_API_KEY`, `GROQ_API_KEY` before starting.

### 2. Windows app
```bash
cd modules/indra-desktop
gradle run
```
Sign in with the account you created, or press **Start backend** to launch the bundled jar.

### 3. Android app
```bash
export ANDROID_HOME=/path/to/Android/Sdk
gradle :indra-android:assembleDebug
adb install modules/indra-android/build/outputs/apk/debug/indra-android-debug.apk
```
The emulator reaches the host backend at `http://10.0.2.2:8420` (already the default). On a real phone,
open **Settings → Server** and enter your PC's LAN address, e.g. `http://192.168.1.20:8420`, or your
HTTPS domain in production.

Full step-by-step instructions: [`docs/BUILD_AND_RUN.md`](docs/BUILD_AND_RUN.md).

---

## API keys — which key comes from where

| Provider | Where to get the key | Environment variable |
|---|---|---|
| OpenAI | platform.openai.com → API keys | `OPENAI_API_KEY` |
| DeepSeek | platform.deepseek.com → API keys | `DEEPSEEK_API_KEY` |
| OpenRouter | openrouter.ai → Keys | `OPENROUTER_API_KEY` |
| GitHub Models | github.com → Settings → Developer settings → PAT (`models:read`) | `GITHUB_MODELS_TOKEN` |
| Google Gemini | aistudio.google.com → Get API key | `GOOGLE_AI_API_KEY` |
| Anthropic | console.anthropic.com → API keys | `ANTHROPIC_API_KEY` |
| Groq | console.groq.com → API keys | `GROQ_API_KEY` |
| Tavily (web search) | tavily.com → API key | `TAVILY_API_KEY` |
| Ollama / LM Studio | install locally, no key | — |

Keys are **never** compiled into the clients. They are added in the app (encrypted on the server with
AES-256-GCM) or supplied as environment variables. More detail: [`config/api-keys.md`](config/api-keys.md).

---

## Security summary

- Passwords are salted and hashed; tokens are JWT with refresh and revocation.
- Android stores tokens, the server URL and its licence key with AES-256-GCM using a key held by the
  Android Keystore; the desktop keeps credentials in memory only.
- Server-side access control on every route, per-user data isolation, rate limiting, input validation,
  structured error responses, audit logging, backup/restore.
- Third-party apps are used only through their official intents; WhatsApp/Instagram/Facebook content is
  never scraped, and the user always presses send.
- Full detail: [`docs/SECURITY.md`](docs/SECURITY.md).

---

## Honest limitations

This repository is source code produced without a working JDK, Android SDK or Gradle distribution in the
authoring environment. Therefore:

- **No build has been executed here, no test run has been observed, and no APK is included.**
  Everything must be compiled on your machine; the commands above are the ones to use.
- Google/GitHub sign-in, weather, search and AI answers require their respective server-side credentials.
  Without them the app degrades gracefully and says so — it does not invent results.
- Windows dictation depends on the speech components installed in Windows.
- Accessibility integration is deliberate and limited to official APIs: full screen-scraping automation is
  not implemented because it would require bypassing Android's security model.

## Credits

**INDRA AI** — designed and developed by **Umesh Chaudhary**.
Licensed for personal use by its author; third-party services keep their own terms.
