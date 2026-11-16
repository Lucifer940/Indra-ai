# INDRA AI architecture

```
+---------------------+        HTTPS/JSON + SSE        +----------------------+
|  Android (Java)     |<----------------------------->|                      |
|  JavaFX desktop     |                              |   indra-backend      |
|  (share indra-core) |                              |   com.sun.net.http   |
+---------------------+                              |                      |
        |                                            |  services: auth,     |
        |  local encrypted storage                   |  chat, memory, task, |
        v                                            |  device, search,     |
+---------------------+                              |  weather, admin,     |
|  SQLite (private)   |                              |  licence, backup     |
+---------------------+                              +----------+-----------+
                                                                |
                                              +-----------------+-----------------+
                                              |  document store (SQLite dev /     |
                                              |  PostgreSQL prod)                 |
                                              +-----------------------------------+
```

## Modules

**indra-core** — no platform dependencies, unit-testable everywhere.
- `model/` — User, UserSettings, Persona, BrainProfile, Language, MemoryItem, TaskItem, Recurrence,
  ChatMessage, Conversation, Device, LicenseRecord, ApiKeyEntry, Role…
- `intent/` — intent classifier (chat, memory, reminder, weather, search, open app, call, SMS, whiteboard,
  study, code, translate, settings…) with risk levels and confirmation flags.
- `ai/` — provider catalogue and factory (OpenAI-compatible, Gemini, Anthropic), prompt builder, personality
  engine (system prompts, greetings, suggested speech rate), streaming orchestrator, offline provider,
  key resolver.
- `memory/` — memory engine (classification, importance, recall scoring) and storage interface.
- `tasks/` — natural-time parsing (EN + NE), recurrence engine, formatting.
- `security/` — hashing, crypto helpers, Base64, token/validation utilities.
- `voice/` — wake-word matcher, speech sanitiser, text language detector.
- `sync/`, `config/`, `license/`, `json/`, `util/` — merge logic, remote config, licence format, JSON.

**indra-backend** — thin HTTP layer over `service/` classes; a JSON document store stores every collection in
one table (`collection`, `id`, `user_id`, `lookup_key`, `updated_at`, `doc`), identical on SQLite and
PostgreSQL. `BackupService` exports/restores all 13 collections. `Main` provides the CLI.

**indra-android** — activities (`Splash`, `Login`, `Main`, `Whiteboard`, `Admin`, `OAuthRedirect`), `ui/`
screens built in Java (chat, voice, memory, tasks, settings) plus the Canvas character, `data/`
(Prefs/KeystoreCipher/LocalStore/ApiClient/SessionManager/DeviceIdentity), `voice/` (TTS + STT),
`service/` (notifications, reminders, boot restore, visible hands-free service), `platform/` (app control,
contacts, file export).

**indra-desktop** — `DesktopApp` (JavaFX UI), `DesktopApi` (HTTP + SSE), `PcControl` (allowlisted Windows
actions), `DesktopVoice` (OS speech), `BackendManager` (starts/stops the server), `CharacterCanvas`,
`DesktopConfig`.

## Request flow for a chat message

1. Client posts to `/api/v1/chat/stream` with persona/brain/language overrides.
2. Server resolves the conversation, stores the user message.
3. The intent classifier runs first. Pure device/tool intents (time, task list, memory list, battery, help)
   are answered deterministically without spending tokens.
4. Otherwise memories relevant to the message, recent history and open tasks are assembled into a prompt via
   `PromptBuilder` + `PersonalityEngine`.
5. `ChatOrchestrator` streams from the configured provider (or the offline brain), forwarding tokens to the
   client while buffering the full text.
6. The assistant message is persisted and the client receives `done` with metadata (`shouldSpeak`,
   `conversationId`).
7. The client may execute a confirmed device action (call, app open, reminder) — the model cannot do that by
   itself.

## Design decisions

- **One core, two clients**: personality, memory scoring, intents and time parsing can never drift between
  Android and Windows.
- **Server-side secrets**: provider keys are per user, encrypted, and never shipped in an APK.
- **Graceful degradation**: no key, no search provider, no speech engine → explicit message, offline brain,
  or a disabled control. Silence and fake results are treated as bugs.
- **No shell**: PC control is an allowlist, not an interpreter.

## Embedded engine (Android)

```
 Android UI (Activities, Views)
        │  HTTP over 127.0.0.1:<os-assigned port>
        ▼
 ApiClient ──► LocalHttpServer (pure Java, keep-alive, chunked SSE)
                       │
                       ▼
              ApiApplication  (same wiring as the PC deployment)
             /        |         \
      AuthService  ChatService  Memory/Task/Settings services
             \        |         /
              Repository ──► EmbeddedStore (JSON files, encrypted via KeystoreFileCodec)
                       │
                 indra-core: prompts, intent, memory engine, providers, crypto
```

The engine starts from `IndraApp.onCreate()`, is reachable only from the phone, and is rebuilt automatically
when Android recreates the process (including after a reboot). Nothing in `indra-core` or the services is
Android-specific; only the transport (`LocalHttpServer`) and the storage codec differ.
