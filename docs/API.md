# INDRA AI REST API (v1)

Base URL: `https://your-server` (development: `http://127.0.0.1:8420`).
All responses are JSON. Errors look like:

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "Email is required", "field": "email" } }
```

Authentication: `Authorization: Bearer <accessToken>` unless marked *public*.

## Health and meta

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/healthz` | public | liveness probe (`ok`) |
| GET | `/api/v1/health` | public | status, version, uptime, provider |
| GET | `/api/v1/version` | public | version and build info |
| GET | `/api/v1/config` | public | remote configuration clients cache |

## Auth

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/register` | create account `{email, password, displayName}` |
| POST | `/api/v1/auth/login` | `{email, password}` → access/refresh tokens, user, settings |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` → new access token |
| POST | `/api/v1/auth/logout` | revoke the current session token |
| GET | `/api/v1/auth/me` | profile + settings |
| PATCH | `/api/v1/auth/me` | update display name / language / persona |
| DELETE | `/api/v1/auth/me` | delete the account and its data |
| POST | `/api/v1/auth/password` | `{currentPassword, newPassword}` |
| GET | `/api/v1/auth/oauth/{provider}/start` | public; returns `authorizeUrl` (PKCE) |
| POST | `/api/v1/auth/oauth/{provider}/exchange` | public; `{code, redirectUri, codeVerifier}` |

`provider` is `google` or `github`.

## Chat

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/chat` | `{message, conversationId?, timeZone, voiceMode, personaId, brainId, languageCode}` |
| POST | `/api/v1/chat/stream` | same body, Server-Sent Events: `meta`, `token`, `done`, `error` |
| GET | `/api/v1/conversations?limit=50` | conversation list |
| GET | `/api/v1/conversations/{id}/messages` | messages |
| DELETE | `/api/v1/conversations/{id}` | delete a conversation |

Non-streaming reply includes `reply`, `conversationId`, `provider`, `model`, `latencyMs`, `offline`,
`intent`, `requiresConfirmation`, `risk`, `shouldSpeak`, and when relevant `memorySaved`,
`suggestedReminderAt`, `suggestedReminderTitle`.

## Memory

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/memories` | list |
| POST | `/api/v1/memories` | `{content, category, importance}` |
| PUT | `/api/v1/memories/{id}` | update |
| DELETE | `/api/v1/memories/{id}` | delete one |
| DELETE | `/api/v1/memories` | clear all |

## Tasks and reminders

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/tasks?includeCompleted=false` | list |
| POST | `/api/v1/tasks` | `{title, remindAt, dueAt, recurrence, timeZone}` |
| PUT | `/api/v1/tasks/{id}` | update |
| POST | `/api/v1/tasks/{id}/complete` | `{timeZone}` → completes and rolls recurring reminders forward |
| DELETE | `/api/v1/tasks/{id}` | delete |

## Settings, keys and catalogue

| Method | Path | Purpose |
|---|---|---|
| GET / PUT | `/api/v1/settings` | personas, brains, language, voice, memory, provider |
| GET | `/api/v1/settings/catalogue` | provider list, personas, brains, languages |
| GET | `/api/v1/keys` | stored keys, masked |
| POST | `/api/v1/keys` | `{provider, label, key, permanent}` |
| DELETE | `/api/v1/keys/{id}` | remove one |
| POST | `/api/v1/keys/{id}/toggle` | `{active}` |
| POST | `/api/v1/keys/reset` | remove all keys for a provider (or all) |

## Devices and sync

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/devices` | paired devices |
| POST | `/api/v1/devices/pair/start` | `{name, platform}` → pairing code |
| POST | `/api/v1/devices/pair/complete` | `{code, name, platform}` → device token |
| POST | `/api/v1/devices/heartbeat` | `{deviceId}` |
| DELETE | `/api/v1/devices/{id}` | revoke |
| POST | `/api/v1/sync` | pull/push memories, tasks, settings, history |

## Tools and licence

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/weather?location=` | current + forecast (`lat`/`lon` supported) |
| GET | `/api/v1/search?q=&news=&limit=` | web/news search with sources |
| POST | `/api/v1/license/activate` | `{key}` |

## Admin (role ADMIN, validated server-side)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/admin/users?q=` | list users |
| PATCH | `/api/v1/admin/users/{id}` | change role / status |
| DELETE | `/api/v1/admin/users/{id}` | delete user |
| GET / PUT | `/api/v1/admin/config` | read / push global configuration |
| GET | `/api/v1/admin/audit?limit=100` | audit trail |
| GET | `/api/v1/admin/stats` | counters |
| GET | `/api/v1/admin/licenses` | licences |
| POST | `/api/v1/admin/licenses` | `{plan, count}` → new codes |
| POST | `/api/v1/admin/licenses/{key}/revoke` | revoke |

### Streaming example

```bash
curl -N -X POST http://127.0.0.1:8420/api/v1/chat/stream \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"hello","timeZone":"Asia/Dubai","voiceMode":false}'
```

```
event: meta
data: {"conversationId":"…","intent":"CHAT","provider":"openai","model":"gpt-4o-mini"}

event: token
data: {"delta":"Hi"}

event: done
data: {"text":"Hi there!","conversationId":"…","shouldSpeak":true}
```
