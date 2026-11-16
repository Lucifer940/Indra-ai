# Changelog — INDRA AI

## 1.1.0 — current

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
