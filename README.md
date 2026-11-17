# INDRA AI

**A personal AI assistant for Android and Windows — one shared core, one secure backend.**
Created by **Umesh Chaudhary**. Current version **1.1.1**.

[![Android build](https://github.com/Lucifer940/Indra-ai/actions/workflows/android-build.yml/badge.svg)](https://github.com/Lucifer940/Indra-ai/actions/workflows/android-build.yml)

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

## Get the Android APK without installing a toolchain

Every push builds the app on GitHub's runners. Open **Actions → Android build → the latest run**:

| Artifact | Contents |
| --- | --- |
| `indra-ai-debug-apk` | `indra-android-debug.apk` + SHA-256 (debug key, installable) |
| `indra-ai-release` | `indra-android-release.apk`, `indra-android-release.aab`, `release-sha256.txt` |
| `test-reports` | JUnit reports for `indra-core` and `indra-backend` + `test-summary.txt` |

Install with `adb install -r indra-android-release.apk`, or copy it to the phone and tap it (allow
"install unknown apps" for your file manager).

The release APK/AAB are signed by CI. If the repository has the four secrets
`INDRA_KEYSTORE_BASE64`, `INDRA_KEYSTORE_PASSWORD`, `INDRA_KEY_ALIAS`, `INDRA_KEY_PASSWORD` they are
signed with your key; otherwise CI generates a throw-away key for that run (installable, but a later
build signed with a different key cannot update it — add the secrets for a stable identity). No key is
ever committed.

## Look and feel

Premium light theme by default (dark available in Settings → Appearance): white surfaces, crimson
`#FF2B4E` primary, pink, violet and electric-blue accents, hairline cards with soft shadows, gradient
primary actions and a holographic orb built from the INDRA character mark.

- **Home** — logo header, notification + settings shortcuts, animated aura orb, greeting, composer,
  quick-action cards for Voice, Memory, Triggers and AI Aura.
- **AI Aura** — six looks (Energy, Galaxy, Fire, Crystal, Neon, Aurora) and four animation speeds,
  saved per device.
- **Bottom navigation** — Home · Chat · Voice · Triggers · Settings, with a floating microphone.
- Brand artwork lives in `modules/indra-android/src/main/res/drawable-nodpi/` (logo, character) and
  the launcher icons in `mipmap-*`; the full-size asset used to generate them is kept out of the APK.

## Run it on the phone — no PC, no account, no server

The phone is self-contained: `indra-backend`, the very same module that runs on a PC, is embedded in the
APK and started on `127.0.0.1` (port assigned by the OS) when the app launches. Rest, auth, rate limiting,
streaming and error handling are unchanged; only the bind address differs.

1. Install the APK and open INDRA.
2. On the login screen tap **Continue without signing in →**. INDRA creates a device session, encrypted
   with the Android Keystore, and opens the main screen immediately.
3. Everything works offline: chat uses the on-device brain, memory / tasks / reminders are stored in the
   app sandbox encrypted at rest, voice and the animated character work as before.
4. Add an API key in **Settings → API keys** to use OpenAI, Gemini, DeepSeek, Groq, Claude, OpenRouter or
   GitHub Models. If the network drops, INDRA says so and answers with the on-device brain instead of
   pretending a cloud model replied.
5. Signing in with an email/Google/GitHub account is optional (Settings → Account, or the login screen).
   **I have a licence key** on the login screen activates a licence without creating an account; you can
   also change it later under Settings → Licence.

Nothing is exposed to the network: the engine binds to loopback only, so no other device can reach it.

## Quick start

The repository ships the Gradle wrapper, so `./gradlew` works everywhere (Gradle 8.7 + JDK 17).

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
../../gradlew run
```
Sign in with the account you created, or press **Start backend** to launch the bundled jar.

### 3. Android app
```bash
export ANDROID_HOME=/path/to/Android/Sdk
cd modules/indra-android && ../../gradlew assembleDebug
adb install -r build/outputs/apk/debug/indra-android-debug.apk
```
The app uses the engine embedded in the phone by default, so no server address is needed. To share one
account with a PC instead, open **Settings → Engine → Server** and enter the PC's LAN address, e.g.
`http://192.168.1.20:8420` (or an HTTPS domain). "Use on-device engine" switches back.

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

- The Android APK/AAB **are** built by CI (`android-build.yml`), debug and signed release, and the
  `indra-core` + `indra-backend` suites compile and pass on the same runners (that job gates the run).
- The embedded engine is covered by `LocalModeIntegrationTest` (loopback bind, OS-assigned port, device
  session, SSE streaming, memory/task persistence across a restart) — see `docs/TESTING.md`.
- No APK has been installed or exercised on a physical Android device from the authoring environment: the
  on-device engine cannot be launched there (no JDK/Android SDK), so "works on a phone" is supported by CI
  builds plus the integration tests, not by a device run. Please report anything a device shows that these
  miss.
- Google/GitHub sign-in, weather, search and AI answers require their respective server-side credentials.
  Without them the app degrades gracefully and says so — it does not invent results.
- Windows dictation depends on the speech components installed in Windows.
- Accessibility integration is deliberate and limited to official APIs: full screen-scraping automation is
  not implemented because it would require bypassing Android's security model.

## Credits

**INDRA AI** — designed and developed by **Umesh Chaudhary**.
Licensed for personal use by its author; third-party services keep their own terms.
