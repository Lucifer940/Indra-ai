# INDRA AI security model

INDRA handles personal memories, reminders and provider keys, so the design assumes a hostile network and
a curious user with a debugger. This document states what is implemented, and what is deliberately not.

## Authentication and sessions

- Passwords are hashed with a per-user salt and many iterations (see `core/security/PasswordHasher`);
  plaintext passwords are never logged or stored.
- Login returns a short-lived JWT access token and a longer-lived refresh token. Tokens carry user id and
  role; the server re-loads the user on every request, so a deleted account loses access immediately.
- `POST /api/v1/auth/logout` revokes the presented token; revocation is stored server-side.
- Login and registration are rate limited separately from ordinary traffic (`INDRA_AUTH_RATE_LIMIT`).
- Administrator rights are **only** decided by the stored role on the server. The client's "admin" flag is
  cosmetic; every admin route re-checks the role and returns `403` otherwise.
- No default or hardcoded administrator exists. An admin is created with the CLI (`create-admin`) using a
  password you supply.

## Secrets

- Provider API keys live on the server, encrypted with AES-256-GCM using `INDRA_MASTER_KEY` before they touch
  the database. The API returns them masked (`sk-…abcd`) and never in full.
- The Android app never contains provider keys. The desktop app never writes credentials to disk.
- OAuth client secrets belong on the server. Clients only hold the public client id and use PKCE.

## Client storage

- Android: access/refresh tokens, the server URL and the licence key are encrypted with AES-256-GCM under a
  key generated inside the Android Keystore (hardware-backed where the device supports it). App backups
  exclude preferences, the database and keys (`backup_rules.xml`, `data_extraction_rules.xml`).
- Memories and tasks are stored in the app's private SQLite database.
- Desktop: the configuration file holds the server URL and UI preferences only; sessions are memory-only.

## Transport

- Production deployments serve HTTPS either directly (PKCS#12 keystore via `INDRA_TLS_KEYSTORE`) or behind a
  reverse proxy.
- Android blocks cleartext traffic by default and only whitelists the emulator loopback hosts
  (`10.0.2.2`, `127.0.0.1`, `localhost`) for local development.
- CORS is restricted to `INDRA_CORS_ORIGINS`.

## Access control and data isolation

- Every repository call is scoped by the authenticated user id; there is no route that returns another
  user's memories, tasks or conversations.
- Device pairing requires a short-lived single-use code plus an authenticated session to revoke a device.
- Input validation happens on the server (length, type, enum membership, JSON shape). Bodies above a size
  limit are rejected.
- Rate limiting applies per IP and per account, with structured `429` responses.

## Logging

- Structured logs with levels; secrets, tokens and passwords are never logged. Provider errors are
  summarised ("401 from provider") instead of dumping request headers.
- Admin actions append to an audit collection exposed at `/api/v1/admin/audit`.

## Device control boundaries

- **Phone:** actions use public Android intents and normal runtime permissions. Calls, SMS, WhatsApp,
  reminders and app launches each require an explicit confirmation dialog in the app. There is no
  accessibility-service automation, no background screen reading and no attempt to bypass Android's
  security model.
- **PC:** only an allowlist of applications can be launched, file operations are confined to the user's home
  directory with protected folders blocked, and destructive operations require confirmation in the UI. The
  model can never execute generated shell commands.
- **Third-party apps** (WhatsApp, Instagram, Facebook, Maps) are opened through their official intents with
  drafts; content is never scraped and messages are sent by the user.

## Voice and privacy

- The microphone opens only for an explicit listening session or while the user has enabled hands-free mode,
  which always shows an ongoing notification and can be switched off instantly.
- No raw audio is written to disk or transmitted; only the on-device recogniser's text is used.
- Hands-free mode stops with the service when the user turns it off, signs out or the OS ends the service.

## Data deletion and backup

- Users can clear memories, delete tasks, delete conversation history and delete their account
  (`DELETE /api/v1/auth/me`).
- `backup` / `restore` CLI commands export/import all collections as JSON; store those files encrypted and
  access-controlled yourself.
- Licences can be revoked by an administrator; revoked keys stop activating clients.

## What is intentionally *not* implemented

- No root, no adb automation, no hidden permissions, no "total access" module that defeats Android's sandbox.
- No automated WhatsApp/Instagram/Facebook messaging (against their terms).
- No scraping of services that forbid it; search results come from the configured search provider's API.

## Reporting

If you find a vulnerability, contact the author, **Umesh Chaudhary**, before public disclosure.
