# Changelog — INDRA AI

## 1.1.2 — current

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
