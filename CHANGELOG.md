# Changelog — INDRA AI

## 1.3.0 — current

### Added
- **Brand identity.** The supplied INDRA AI artwork is now the launcher icon (all densities,
  round + adaptive foreground), the in-app header logo, the splash artwork and the orb core.
  Tagline is "YOUR PERSONAL AI ASSISTANT".
- **Premium light theme** (default) with a crimson/pink/violet/electric-blue palette, hairline
  glass cards, gradient primary buttons, circular icon buttons and a dark variant. Theme is
  switchable in Settings → Appearance and applied at startup (`UiKit.applyTheme`).
- **New Home screen** (`HomeScreen`): logo header, notification and settings shortcuts, large
  animated aura orb, personalised greeting, composer with attachment shortcut, "More" cards
  (Voice Mode, Memory, Triggers, AI Aura) and a quick-tools card (whiteboard, weather, history,
  API keys). Every control opens a feature that exists.
- **AI Aura**: six orb looks (Energy, Galaxy, Fire, Crystal, Neon, Aurora) plus animation speed,
  drawn by `AuraOrbView` (rotating sweep, breathing rings, speaking pulse) and saved per device.
- **Five-tab shell**: Home / Chat / Voice / Triggers / Settings with a floating microphone that
  is always one tap from listening. Memory stays reachable from Home and Settings.
- Notifications panel (upcoming reminders + system notification settings) from the Home bell.

### Fixed
- **Login from Settings**: an account is no longer a one-way door. Settings → Account shows
  *Sign in or create account* for device sessions (and *Switch account* otherwise), and the
  login screen can now be opened on top of an existing session (`EXTRA_FORCE_SHOW`), returning
  to a rebuilt main screen afterwards.
- Orb animation stops when the view is off-screen instead of redrawing forever.

## 1.2.0

### Added
- **The backend now runs inside the Android app.** `indra-backend` is a dependency of the app and
  `ApiApplication` starts on `127.0.0.1` with an OS-assigned port at launch (`LocalBackend`), so the phone
  needs no PC, emulator host or external server. REST routes, JWT auth, rate limiting, SSE streaming and
  error mapping are the same code path as the desktop deployment.
- **"Continue without signing in"** on the login screen: one tap creates an encrypted device session
  (`POST /api/v1/auth/local`, available only in local mode) and opens the main screen. Email/Google/GitHub
  sign-in and the licence remain optional.
- **"I have a licence key"** on the login screen activates a licence without creating an account (the
  device session is created first); the key is stored Keystore-encrypted and can be changed in Settings.
- The test job also compiles the Windows desktop module, so shared-code changes cannot silently break it.
- Portable `ai.indra.core.net.Http` (HttpURLConnection) used by every network call, replacing
  `java.net.http`, which does not exist on Android.
- Pure-Java HTTP/1.1 server (`LocalHttpServer` + `Exchange`): keep-alive, chunked request bodies and chunked
  Server-Sent Events; replaces `com.sun.net.httpserver`.
- At-rest encryption of the on-device store: every collection file is written as
  `INDRA1:<AES-256-GCM>` with a key held by the Android Keystore (`KeystoreFileCodec`). JWT signing and the
  API-key master key are random per install and stored Keystore-encrypted - nothing is hard-coded.
- Signed release APK + AAB in CI (`Release APK + AAB` job, artifact `indra-ai-release`) using repository
  secrets when present and a throw-away key otherwise.
- `LocalModeIntegrationTest`: loopback bind, OS-assigned port, device session, SSE streaming and
  memory/task persistence across an engine restart.

### Changed
- The app talks to its own engine by default; a remote server is an optional setting
  (**Settings → Engine**), no longer the default. `API_BASE_URL` is now only a fallback.
- `ChatService` falls back to the on-device brain on network/timeout provider errors and says so in the
  reply instead of failing or implying a cloud answer.
- Release builds are no longer minified (`minifyEnabled false`): shrinking is unverified on a device, and a
  working build matters more than a smaller one.

### Fixed
- `SseResponder` no longer throws when the client disconnects mid-stream.

## 1.1.2

### Fixed
- `indra-backend` and `indra-core` test sources now compile: `BackendConfig` copies settings into its final map
  and returns `long` from `getLong`, `KeyService` has a real `audit(...)` helper, `JsonArray` gained
  `getDouble`, and the sync/config tests pass `JsonObject` straight to `fromJson`.
- `ChatService` handles a client disconnecting mid-stream; the OpenAI-compatible provider handles
  `InterruptedException`.
- Core behaviour bugs the suite caught: `remind me to …` no longer swallows the `to` of `tomorrow`;
  memory classification recognises allergies and phone numbers, keeps Devanagari matras in tokens,
  canonicalises spelling variants and ranks query matches above pinned notes; the wake word accepts
  Devanagari and returns lowercase commands; the speech sanitiser strips leftover markdown, URLs and emoji;
  the system prompt no longer hardcodes a version or a canned AI disclaimer.
- Corrected two test expectations that contradicted their own comments (08:00 rollover and settings-merge
  symmetry).

### Changed
- The test job is no longer `continue-on-error`: the APK job still publishes independently, but a failing
  test suite turns the run red. The job also writes a test-count summary (`test-summary.txt`, job summary).

## 1.1.1

### Added
- Gradle wrapper 8.7 (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) so no local Gradle install is needed.
- GitHub Actions workflow `.github/workflows/android-build.yml`: builds the debug APK on every push,
  verifies its contents, publishes it as the `indra-ai-debug-apk` artifact with a SHA-256, and reports
  compile errors back as a commit comment. A separate non-blocking job runs the core/backend tests.
- `.gitattributes` keeps `gradlew` LF and `gradlew.bat` CRLF.

### Fixed
- **The Android `data` package was missing from the repository**: the `.gitignore` entry `data/` (meant for
  the backend's runtime folder) also matched `modules/indra-android/src/main/java/ai/indra/android/data/`,
  so `Prefs`, `ApiClient`, `LocalStore`, `KeystoreCipher`, `SessionManager` and `DeviceIdentity` were never
  committed. They are restored, the ignore rules are now anchored to the repository root, and the app
  compiles.
- `Settings.ACTION_NOTIFICATION_SETTINGS` (does not exist) replaced with
  `android.settings.APP_NOTIFICATION_SETTINGS`.
- `Strings.mask` → `Strings.maskSecret` in the settings screen.
- `WhiteboardActivity.DrawingView` now takes a `Context` and calls `super(context)`.
- Root `build.gradle` reads `VERSION` from the repository root instead of each module directory.
- Backend dependency uses `implementation` instead of the unavailable `api` configuration.

## 1.1.0

### Added
- Android app: splash, login/register with Google + GitHub OAuth hooks (PKCE), chat with streaming,
  voice screen with animated character, memory, tasks/reminders, settings, whiteboard, admin console.
- Animated female character (Canvas) with blinking, gaze drift, expressions, listening/thinking/speaking
  states and speech-synchronised mouth movement on both platforms.
- Hands-free wake word ("Indra") via a visible foreground service, with instant off switch.
- Multiple API keys per user: add, mask, toggle, remove, reset; stored AES-256-GCM encrypted server-side.
- Licence key system: admin-generated codes, activated once and remembered per device.
- Device pairing (code based), device list, revoke, and memory/task/settings sync.
- Tasks with natural-language times in English and Nepali, recurrence, alarm scheduling, boot restore.
- Phone control: allowlisted app launch, system settings, battery/system info, share, confirmed calls/SMS,
  WhatsApp drafts.
- JavaFX Windows app: dashboard, chat, character, voice (OS speech), memory, history, files, settings,
  backend start/stop, pairing, licence, logout, window controls.
- PC control allowlist: apps, folders, file search/create/rename/copy/move/delete (confirmed), URLs, system
  info, lock workstation.
- Backend: admin API (users, config push, audit, stats, licences), backup/restore, CLI (serve, create-admin,
  issue-license, backup, restore, license-keygen), SQLite/PostgreSQL schema, docs.
- Core test suite (11 classes) and a backend end-to-end HTTP test suite.
- Documentation: README, build/run, API, security, testing, architecture, API-key guide.

### Changed
- HTTP server uses daemon threads so CLI and test JVMs exit cleanly.
- `BackendConfig` can be built from a map for embedding and tests.

## 1.0.0
- Initial shared core, backend, and project scaffolding.
